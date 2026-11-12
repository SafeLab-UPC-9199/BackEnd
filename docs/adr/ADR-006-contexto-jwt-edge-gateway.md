# ADR-006: Contexto JWT real en el edge gateway y confianza en headers (Fase 6)

**Estado:** Aceptado  
**Fecha:** 2026-11-08  
**Decisores:** Equipo de desarrollo SafeLab  
**Revisa:** ADR-005 (RabbitMQ/DLQ), ADR-003 (eventos de dominio)

## Contexto

Hasta la Fase 6 los 24 rutas del gateway (`:8080`) enrutan **sin autenticación**: cualquier cliente podía leer y mutar laboratorios, incidentes, perfiles y configuración sin token. Los microservicios, además, no tienen Spring Security (solo `backend-iam`): los adaptadores de contexto (`JWTSecurityContextAdapter` en `alerts`) eran *stubs* que devolvían `system-automation`, workspace vacío y `admin=true`.

Dos huecos concretos del ADD:

1. **`POST /api/v1/laboratories` roto**: `Laboratory.workspace` es `nullable=false` pero el comando nunca lo asignaba (en el monolito lo resolvía `CurrentWorkspaceService` → `UserProfile.workspaceId`).
2. **Sin contexto de identidad/tenant**: ningún servicio sabía quién llamaba ni a qué workspace pertenecía.

La pregunta de diseño era **dónde** validar el token:

- **A) Validación local en el gateway** con el secreto HS256 compartido con `backend-iam`.
- **B) Introspección contra iam en cada petición** (Feign/WebClient).

## Decisión

**Opción A: el gateway valida el JWT localmente y propaga identidad por headers.**

1. **Filtro global `JwtAuthenticationGlobalFilter` (gateway).**
   - Lee `authorization.jwt.secret` (mismo valor y algoritmo HS256 que `TokenServiceImpl` de iam) y verifica firma + `exp` con JJWT en **cada petición protegida**.
   - Rutas públicas: `sign-in`, `sign-up`, `confirm-account`, `resend-code`, alias legacy `/api/v1/auth/**`, `/api/v1/system/status`, `/api/v1/telemetry/ingestion` (los dispositivos usan `X-Api-Key`), el actuator del gateway y los preflight CORS. Todo lo demás exige Bearer válido → **401** sin token.
   - **Anti-spoofing**: elimina del request cualquier `X-User-Email`, `X-User-Id`, `X-User-Roles`, `X-Workspace-Id` o `X-Actor` enviado por el cliente y los reemplaza por los claims verificados. `Authorization` se reenvía sin cambios porque `backend-iam` valida sus propios endpoints protegidos.

2. **Claims emitidos por iam al firmar el token** (`generateToken(email, roles, workspaceId)`):
   - `sub` = email (ya existía),
   - `roles` = nombres `ROLE_*` del usuario,
   - `workspaceId` = de `safelab_automation.user_profiles`, *best-effort* (si aún no hay perfil, la claim se omite).
   - Los tokens legacy (solo `sub`) siguen siendo válidos: los claims nuevos son opcionales.

3. **Los servicios leen el contexto en una sola costura**: la clase estática `RequestIdentity` (backend-shared) expone `currentUserEmail()`, `currentWorkspaceId()`, `currentRoles()`, `isAdmin()` leyendo los headers del request servlet actual. `JWTSecurityContextAdapter` (alerts) delega en ella conservando sus getters.

4. **Semilla de workspace en labs** (`WorkspaceSeeder`): crea `SafeLab Base Workspace` (`code=base-operator-workspace`), el mismo que usaba el seeder del monolito. `LaboratoryCommandServiceImpl` resuelve el workspace del llamador: header `X-Workspace-Id` si existe → `findById`; si no → el workspace base. Un header apuntando a un workspace inexistente es error, no fallback silencioso.

5. **Secreto unificado**: compose inyecta `JWT_SECRET` (la variable que la app realmente lee; `AUTHORIZATION_JWT_SECRET` no era leída por ningún código) tanto en `safelab-backend-iam` como en `safelab-gateway`, con el mismo default que el monolito.

## Consecuencias

**Positivas:**
- El perímetro deja de estar abierto: sin token no hay acceso a las 24 rutas protegidas, y los headers que los servicios confían ya no pueden ser falsificados desde el cliente.
- Cero latencia añadida por autenticación (HMAC local, sin salto a iam) y **iam no se vuelve punto único de fallo** del tráfico.
- `POST /api/v1/laboratories` funciona y las creaciones quedan asociadas a un workspace real.
- El mecanismo de tenant está definido en un solo punto (`RequestIdentity`) listo para que compliance/history/automation lo adopten.

**Negativas (aceptadas y documentadas):**
- **Los servicios confían en los headers**: quien alcance un puerto intero directamente (hoy publicados en el host: 8081–8087) puede falsear el contexto. En producción esos `ports` se retiran del compose; aquí se mantienen porque los drills del curso apuntan a `:8081`/`:8082`. Es un límite de confianza del stack local, no de producción.
- **Revocación diferida**: un token es válido hasta `exp` (50 días). Mitigaciones posibles (TTL corto, lista de revocación cacheada) quedan fuera de esta fase.
- **Los adaptadores dejan de ser stubs**: `alerts` pasa de `admin=true` siempre a leer el rol real; con sin cabeceras (consumidor Rabbit, acceso directo) se conservan los defaults históricos para no romper los flujos existentes.
- El scoping por workspace solo se activa si el token trae `workspaceId` (hoy: ningún usuario tiene perfil en automation, así que las queries siguen sin scoping). Reactivar el scoping completo (update/delete de labs, audit trails de compliance) es trabajo pendiente explícito.
- Si el frontend no envía `Authorization` en todas sus llamadas, verá 401 tras el despliegue.

## Alternativas consideradas

- **Introspección por petición contra iam (B):** rechazado: +1 salto en las 24 rutas, Feign bloqueante dentro de un gateway WebFlux y caída de iam = caída de todo el tráfico (precisamente lo que ADR-004 mitiga con circuit breakers). El único beneficio —revocación inmediata— se puede obtener con TTL corto o una caché de revocación.
- **Spring Security Resource Server en el gateway:** rechazado por traer todo el stack de seguridad para un solo filtro; JJWT ya estaba gestionado en el root pom y lo usa iam.
- **Servicios validando el token ellos mismos:** rechazado: repite el filtro en 7 servicios y cada uno necesitaría el secreto.
- **Poner `workspaceId` en `user_profiles` desde iam al registrarse:** rechazado (iam escribiendo en el esquema de automation); la lectura *best-effort* en el login mantiene la escritura donde vive el dato.
