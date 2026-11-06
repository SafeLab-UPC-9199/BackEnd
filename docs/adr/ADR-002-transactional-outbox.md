# ADR-002: Patrón Transactional Outbox transversal

**Estado:** Aceptado  
**Fecha:** 2026-11-05  
**Decisores:** Equipo de desarrollo SafeLab

## Contexto

El informe ADD (Iteración 0) requiere fiabilidad y trazabilidad en la publicación de eventos entre bounded contexts. La arquitectura de microservicios introduce el desafío de mantener consistencia entre una base de datos local y la publicación de eventos a otros servicios sin usar transacciones distribuidas (2PC), que no son viables en esta arquitectura.

## Decisión

Se implementa el patrón Transactional Outbox como infraestructura transversal en el módulo `backend-shared`:

1. **Entidad SharedEventOutbox:** Tabla en el esquema `safelab_shared` que almacena eventos pendientes de publicar (eventId, eventType, aggregateType, aggregateId, payload, correlationId, schemaVersion, status, occurredAt, retryCount, lastError).

2. **Despachador programado:** `@Scheduled` que publica eventos con estado `PENDING` a través de `ApplicationEventPublisher`, marcándolos como `PUBLISHED` o `FAILED` según el resultado.

3. **Reintentos y dead-letter:** Máximo 5 reintentos; eventos que fallan repetidamente pasan a estado `FAILED` para inspección manual.

4. **Purga automática:** Cron configurable para eliminar eventos `PUBLISHED` antiguos (por defecto 7 días).

5. **Uso transaccional:** El guardado de la entidad de dominio y el registro en la tabla Outbox ocurren en la misma transacción local (@Transactional), garantizando atomicidad.

## Consecuencias

**Positivas:**
- Consistencia eventual garantizada sin transacciones distribuidas
- No se pierden eventos en caso de falla del despachador
- Trazabilidad completa de eventos publicados y fallidos
- Visibilidad operativa mediante endpoint `/api/v1/alerts/outbox/stats`

**Negativas:**
- Duplicación de datos (payload se almacena en Outbox además de en la tabla de dominio)
- Retraso entre escritura y publicación (depende del intervalo del despachador)
- Requiere limpieza periódica para evitar crecimiento ilimitado de la tabla

## Alternativas consideradas

- **Transacciones distribuidas (2PC):** Rechazado por incompatibilidad con microservicios y complejidad operativa
- **Event sourcing completo:** Rechazado por sobrecarga de implementación para el alcance del proyecto
- **Cambio de estado dual (CDC) con Debezium:** Rechazado por requerir infraestructura adicional (Kafka, Connect)
