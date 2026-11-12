# Testing Suite Evidence for Sprint Review

**Proyecto:** SafeLab – 1ASI0657 – Proyecto de Curso  
**Universidad:** Universidad Peruana de Ciencias Aplicadas (UPC)  
**Usuario:** alejandro202312510 (u202312510@upc.edu.pe)  
**Fecha:** 05/11/2026  

## 1. Repositorios de control de versiones

| Repositorio | Rama | Commit Id | Commit Message | Commit Message Body | Commited on (Date) |
|-------------|------|-----------|----------------|---------------------|---------------------|
| SafeLab-UPC-9199/BackEnd | feat/add-iteration-0-1 | [PENDIENTE] | feat: complete iterations 0 and 1 (outbox shared, events decoupling, idempotency, mqtt resilience) | Implementación de Iteraciones 0 y 1 del ADD: Outbox transversal, eventos de dominio, idempotencia HTTP/MQTT, resiliencia MQTT | 05/11/2026 |
| SafeLab-UPC-9199/BackEnd | develop | [PENDIENTE] | merge: complete iterations 0 and 1 | Merge de feat/add-iteration-0-1 a develop | 05/11/2026 |
| SafeLab-UPC-9199/BackEnd | main | [PENDIENTE] | merge: develop into main | Merge de develop a main | 05/11/2026 |
| SafeLab-UPC-9199/FrontEnd | main | [PENDIENTE] | feat: implement frontend modules aligned with ADD iterations | Módulos frontend alineados con bounded contexts del ADD | 05/11/2026 |

## 2. Archivos de pruebas BDD (.feature) diseñados

### backend-telemetry/src/test/resources/features/telemetry-ingestion.feature

**User Story relacionada:** US05 – Monitoreo térmico (Sprint 1)

Escenarios incluidos:
1. **Ingestión exitosa de lectura de temperatura por HTTP** – Valida que una lectura válida sea aceptada (202 ACCEPTED).
2. **Rechazo por sensor no registrado** – Valida que sensores no registrados devuelvan 404.
3. **Idempotencia** – Valida que reenviar la misma Idempotency-Key no duplique el procesamiento.
4. **Rechazo por API Key inválida** – Valida autenticación del dispositivo edge (401).

### backend-alerts/src/test/resources/features/alert-management.feature

**User Story relacionada:** US08 / US24 – Alertas e incidentes (Sprint 1)

Escenarios incluidos:
1. **Crear una alerta de temperatura crítica** – Valida detección de excursión térmica y creación de alerta.
2. **Crear un incidente a partir de una alerta** – Valida vinculación alerta-incidente.
3. **Escalar incidente cuando no se resuelve a tiempo** – Valida escalación automática.
4. **Resolver incidente con acción correctiva** – Valida cierre de incidente.

### backend-iam/src/test/resources/features/authentication.feature

**User Story relacionada:** US04 – Acceso al sistema (Sprint 1)

Escenarios incluidos:
1. **Registro exitoso de nuevo usuario** – Valida creación de cuenta (201).
2. **Inicio de sesión exitoso** – Valida autenticación y devolución de JWT (200).
3. **Rechazo por credenciales inválidas** – Valida rechazo de contraseña incorrecta (401).
4. **Registro duplicado debe fallar** – Valida unicidad de email (409).

## 3. Clases de prueba JUnit existentes

Además de los archivos .feature, el backend incluye pruebas unitarias JUnit en los siguientes servicios:

| Servicio | Clase de prueba | Cobertura |
|----------|-----------------|-----------|
| telemetry | TelemetryIngestionControllerTest | Endpoint de ingesta HTTP |
| telemetry | TelemetryIngestionServiceImplTest | Servicio de ingesta |
| alerts | EvaluateReadingUseCaseImplTest | Evaluación de lecturas y umbrales |
| alerts | IncidentCommandServiceImplTest | Comandos de incidentes |
| alerts | OutboxAdminControllerTest | Endpoints de administración del Outbox |
| iam | Pruebas de autenticación y autorización | Login, registro, JWT |
| compliance | Pruebas de auditoría y reportes | Audit trails |
| labs | Pruebas de gestión de laboratorios | CRUD de activos |
| automation | Pruebas de reglas y umbrales | Automatización |
| history | Pruebas de registros históricos | Trazabilidad |

## 4. Cómo ejecutar las pruebas

### Pruebas BDD (Cucumber)

Cada servicio tiene un runner de Cucumber:

- `backend-telemetry/src/test/java/com/test/backend/telemetry/test/bdd/TelemetryCucumberTest.java`
- `backend-alerts/src/test/java/com/test/backend/alerts/test/bdd/AlertsCucumberTest.java`
- `backend-iam/src/test/java/com/test/backend/iam/test/bdd/IamCucumberTest.java`

Comando para ejecutar pruebas de un servicio:
```bash
mvn -f backend-telemetry/pom.xml test -Dtest=TelemetryCucumberTest
```

### Pruebas unitarias JUnit

Comando para ejecutar todas las pruebas de todos los servicios:
```bash
mvn -f pom.xml test
```

## 5. Evidencias de Swagger

Para capturas de pantalla de Swagger (requeridas para el Sprint Review):

1. Abrir: `https://safelab-backend.onrender.com/swagger/swagger-ui/index.html`
2. O ejecutar localmente: `mvn spring-boot:run` en cada servicio y abrir `http://localhost:<puerto>/swagger/index.html`
3. Endpoints clave para capturar (Iteración 1):
   - `POST /api/v1/telemetry/ingestion` – Con headers X-Api-Key e Idempotency-Key
   - `GET /api/v1/alerts/outbox/stats` – Estadísticas del Outbox
   - `GET /api/v1/alerts/outbox/failed` – Eventos fallidos del Outbox

## 6. Trazabilidad User Story → Prueba

| User Story | Sprint | Archivo .feature | Escenario | Servicio |
|------------|--------|------------------|-----------|----------|
| US04 – Autenticación | 1 | authentication.feature | Registro, login, credenciales inválidas, duplicado | iam |
| US05 – Monitoreo térmico | 1 | telemetry-ingestion.feature | Ingestión, sensor no registrado, idempotencia, API Key inválida | telemetry |
| US08 – Alertas | 1 | alert-management.feature | Alerta crítica, incidente, escalación, resolución | alerts |
| US24 – Incidentes | 1 | alert-management.feature | Crear incidente, escalar, resolver | alerts |

## 7. Notas

- Los archivos `.feature` utilizan lenguaje Gherkin (Given/When/Then).
- Los step definitions están implementados en Java bajo cada módulo de servicio.
- Los commits de testing están asociados a las ramas `feat/add-iteration-0-1`, `develop` y `main` del repositorio BackEnd.
- El frontend no incluye pruebas BDD adicionales; su validación se realiza a través de los endpoints del backend expuestos por el API Gateway.
