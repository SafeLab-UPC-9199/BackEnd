# Plan: Completar Iteraciones 0 y 1 para SafeLab (Backend + Frontend)

## Objetivo
Implementar las mejoras necesarias para considerar las Iteraciones 0 y 1 "completas": 
- Iteración 0: transversalizar el patrón Transactional Outbox, garantizar uso transaccional, añadir limpieza/observabilidad
- Iteración 1: desacoplar telemetry->alerts con eventos, añadir idempotencia, resiliencia MQTT, validación/rate limiting, mayor robustez

## Ámbito
- Backend (Spring Boot): módulos `shared`, `alerts`, `telemetry`, `labs` (según corresponda)
- Frontend (Angular): ajustes mínimos si hay integración con nuevos endpoints/eventos
- Configuración: `application.properties`, `docker-compose.yml` si necesario

## Estado actual
- Outbox existe en `alerts` (`EventOutbox`, `EventOutboxDispatcher`, usado por `IncidentCommandServiceImpl`)
- Ingesta HTTP + API Key en `telemetry` (`TelemetryIngestionController`, `TelemetryIngestionService`)
- MQTT v5 TLS configurado en `docker-compose.yml`
- Evaluación de lecturas en `alerts` vía `EvaluateReadingUseCaseImpl`

## Tareas

### 1) Iteración 0 - Transactional Outbox
1.1 **Transversalizar Outbox a `shared`**
- Mover/crear `shared/infrastructure/messaging/outbox/` con `EventOutbox`, `EventOutboxRepository`, `EventOutboxDispatcher` genéricos (o interfaces reutilizables)
- Mantener compatibilidad o migrar referencias desde `alerts`

1.2 **Garantizar atomicidad**
- Asegurar `@Transactional` en servicios que escriben entidad + outbox (ej. `IncidentCommandServiceImpl`, otros casos)
- Documentar patrón con comentario breve

1.3 **Limpieza y observabilidad**
- Añadir método para purgar eventos `PUBLISHED` antiguos (configurable: `outbox.purge-after-days`)
- Añadir métricas/logs para eventos `FAILED` (contador/log estructurado)
- Exponer información vía Actuator si aplica

### 2) Iteración 1 - Robustez de ingesta y desacoplamiento
2.1 **Eventos de dominio para desacoplar telemetry->alerts**
- Crear evento `TemperatureReadingRecorded` (domain event) en `telemetry/domain/model/events/`
- Publicar evento desde servicio de ingesta/processing en `telemetry` (en lugar de llamar directamente a `alerts` use case)
- Escuchar con `@EventListener`/`@TransactionalEventListener` en `alerts` para evaluar lecturas
- Eliminar o minimizar acoplamiento directo

2.2 **Idempotencia en ingesta**
- Añadir soporte a `Idempotency-Key` header en `TelemetryIngestionController`
- Crear entidad `TelemetryIngestionIdempotency` (shared/telemetry) con clave única, respuesta cacheada, expiración
- Rechazar o retornar respuesta cacheada en caso de reintento

2.3 **Resiliencia MQTT**
- Revisar cliente MQTT (v5 TLS) en `telemetry/infrastructure/messaging/`
- Añadir reconexión automática, backoff exponencial, heartbeat/keepalive
- Manejo de desconexión/reconexión con logs
- Configuración via `application.properties`

2.4 **Validación, rate limiting y límites**
- Validar payload estrictamente (rangos, tipos) en ingesta HTTP/MQTT
- Añadir rate limiting por API key/dispositivo (simple in-memory o filtro)
- Rechazar lecturas inválidas con códigos apropiados

2.5 **Observabilidad**
- Añadir Spring Boot Actuator si no existe
- Añadir correlationId propagation (si no existe)
- Logs estructurados para ingesta/evaluación

### 3) Frontend (ajustes mínimos)
- Verificar que llamadas HTTP sigan funcionando
- Si se añaden headers (Idempotency-Key), asegurar envío desde dispositivos/cliente según corresponda
- Actualizar tipos si es necesario

### 4) Configuración
- Añadir props: `outbox.dispatch-interval-ms`, `outbox.max-retries`, `outbox.purge-after-days`, `telemetry.idempotency.ttl-minutes`, `mqtt.*` resiliencia
- Actualizar `docker-compose.yml` solo si necesario

## Validación
- Compilar backend: `mvn clean compile` / `./mvnw compile`
- Ejecutar tests relevantes
- Probar ingesta HTTP con API key
- Verificar despachador outbox funciona
- Verificar eventos desacoplados funcionan
- Comprobar idempotencia con mismo header
- Revisar logs MQTT reconexión

## Riesgos
- Migración de Outbox entre contextos puede romper imports
- Cambio a eventos puede romper flujo existente si listeners no están configurados
- Idempotencia requiere persistencia
- MQTT cambios pueden afectar conexión

## Entregable
Código implementado cumpliendo mejoras para Iteraciones 0 y 1, con estructura mantenida (monolito modular por bounded contexts). Commits descriptivos.
