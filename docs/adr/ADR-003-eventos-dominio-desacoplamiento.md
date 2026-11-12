# ADR-003: Eventos de dominio para desacoplamiento telemetry -> alerts

**Estado:** Aceptado  
**Fecha:** 2026-11-05  
**Decisores:** Equipo de desarrollo SafeLab

## Contexto

El informe ADD (Iteración 1) establece que la ingesta de telemetría y la generación de alertas son bounded contexts separados. En el monolito modular inicial, `telemetry` llamaba directamente a los casos de uso de `alerts` para evaluar lecturas y generar incidentes, creando acoplamiento directo entre servicios.

## Decisión

Se implementa desacoplamiento mediante eventos de dominio:

1. **Evento TemperatureReadingRecorded:** Publicado por el servicio `telemetry` tras persistir una lectura válida. Contiene readingId, laboratoryId, metricTypeId, metricKey, value, recordedAt y campos opcionales para umbrales y nombre del laboratorio.

2. **Consumidor en alerts:** recibe `TemperatureReadingRecorded` y ejecuta `EvaluateReadingUseCase` para evaluar si se debe generar una alerta o incidente. En el monolito es un `@EventListener`; en la topología de microservicios (Fase 5 / ADR-005) es un `@RabbitListener` sobre RabbitMQ, porque el consumidor corre en otro proceso.

3. **Sin acoplamiento directo:** `telemetry` no depende de `alerts` ni hace llamadas Feign a él. La comunicación es unidireccional a través del evento.

## Consecuencias

**Positivas:**
- `telemetry` y `alerts` son independientes: uno puede caer sin afectar al otro
- Fácil agregar nuevos consumidores del evento (history, compliance, automation) sin modificar `telemetry`
- Pruebas unitarias aisladas por servicio

**Negativas:**
- Pérdida de consistencia inmediata: si `alerts` está caído cuando se publica el evento, la alerta no se genera hasta que se reprocesa el evento (mitigado con Outbox)
- Mayor complejidad de depuración distribuida (mitigado con correlationId)

## Alternativas consideradas

- **Llamada síncrona Feign de telemetry a alerts:** Rechazado por crear acoplamiento sincrónico y punto de falla en cadena
- **Cola de mensajes externa (RabbitMQ/Kafka):** Rechazado por requerir infraestructura adicional no disponible en el alcance académico → **subsanado por ADR-005**, que incorpora RabbitMQ al stack local y sustituye el `@EventListener` in-process por un `@RabbitListener` con DLQ e idempotencia.
