# ADR-005: RabbitMQ como broker, DLQ e idempotencia del consumidor (Fase 5)

**Estado:** Aceptado  
**Fecha:** 2026-11-07  
**Decisores:** Equipo de desarrollo SafeLab  
**Revisa:** ADR-002 (outbox transversal), ADR-003 (eventos de dominio)

## Contexto

El informe ADD (Iteración 1) exige que `telemetry` y `alerts` sean bounded contexts separados. ADR-003 desacopló ambos mediante el evento `TemperatureReadingRecorded`, pero lo resolvió **in-process**: el evento se publicaba con `ApplicationEventPublisher` dentro de la misma JVM. En el monolito eso funciona; en la topología de microservicios los dos servicios corren en contenedores distintos, de modo que el consumidor de `alerts` nunca recibía nada (solo se escuchaba a sí mismo) y cualquier caída del consumidor perdía el evento sin rastro.

ADR-003 descartó «cola de mensajes externa (RabbitMQ/Kafka)» por infraestructura adicional fuera del alcance académico. Esa restricción ya no aplica: se incorpora RabbitMQ al stack local (`docker-compose`) para dar transporte real, supervivencia a caídas y observabilidad de los mensajes fallidos.

## Decisión

1. **Broker RabbitMQ en el stack (ADR-005).**
   - Nuevo servicio `safelab-rabbitmq` (imagen `rabbitmq:3.13-management`, puerto 5672 y consola 15672 con usuario `safelab`), consumido por `backend-telemetry` (productor) y `backend-alerts` (consumidor). Los otros seis servicios no llevan `spring-boot-starter-amqp`: su classpath y su actuator siguen libres de RabbitMQ.

2. **Topología idempotente declarada por ambos lados.**
   - Exchange de topic `safelab.events` con routing key `telemetry.temperature.reading.recorded`.
   - Cola durable `alerts.temperature.reading.recorded` con `x-dead-letter-exchange=safelab.events.dlx` y `x-dead-letter-routing-key=alerts.temperature.reading.recorded.dlq`.
   - Exchange directo `safelab.events.dlx` y cola `alerts.temperature.reading.recorded.dlq`.
   - Productor y consumidor declaran exactamente los mismos objetos (constantes compartidas en `MqTopology`, de `backend-shared`, sin dependencia AMQP). Declarar es idempotente en RabbitMQ: el orden de arranque deja de importar y cualquiera de los dos puede reconstruir la topología tras un borrado del broker.

3. **Publicación duradera a través del outbox (ADR-002), no directa.**
   - `TelemetryIngestionServiceImpl` escribe `TemperatureReadingRecorded` en `safelab_shared.event_outbox` **dentro de la misma transacción** que persiste la lectura (estado `PENDING`).
   - `SharedEventOutboxDispatcher` (importado y habilitado con `@EnableScheduling` en `backend-telemetry`) drena esas filas y las entrega a `OutboxRabbitPublisher`, que las publica en `safelab.events` con `messageId = eventId` y entrega persistente.
   - Si el broker está caído, la excepción se propaga: la fila **no** se marca `PUBLISHED`, se incrementa `retry_count` y se reintenta en la siguiente pasada. La entrega es *at-least-once*, nunca *at-most-once*.
   - Como el outbox es una tabla única compartida, el despachador acepta `outbox.dispatch-event-types`: `backend-telemetry` solo drena `TemperatureReadingRecorded` y `backend-shared` solo los `INCIDENT_*`, de modo que dos despachadores jamás marcan `PUBLISHED` la misma fila sin que exista un único responsable de publicarla.

4. **Consumidor idempotente con inbox (`processed_events`).**
   - `TemperatureReadingEventHandler` pasa de `@EventListener` a `@RabbitListener(queues = "alerts.temperature.reading.recorded")`.
   - Antes de evaluar, busca la clave (`messageId`; si no existe, `reading-<readingId>`) en la tabla `processed_events`. La clave tiene índice único, por lo que dos entregas simultáneas no pueden producir dos efectos.
   - La fila del inbox, el incidente y el outbox `INCIDENT_*` de `alerts` comparten transacción: si la evaluación falla, todo se revierte y el mensaje puede reprocesarse (o acabar en la DLQ) sin quedar registrado como procesado.

5. **Reintentos y DLQ.** El contenedor del listener reintenta 3 veces (1 s ×2, tope 4 s) y luego rechaza **sin requeue** (`spring.rabbitmq.listener.simple.default-requeue-rejected=false`), de modo que RabbitMQ dead-lettera el mensaje a `alerts.temperature.reading.recorded.dlq`. El listener ya no traga excepciones: un fallo de evaluación se propaga, en lugar de desaparecer en una línea WARN. La DLQ centraliza los mensajes que hay que inspeccionar o reprocesar.

## Consecuencias

**Positivas:**
- `telemetry -> alerts` funciona de verdad entre procesos: las lecturas generan incidentes sin ninguna llamada HTTP entre los dos servicios.
- Un broker caído no pierde lecturas (filas `PENDING` en el outbox) y un consumidor caído no las pierde (se acumulan en la cola durable).
- Los mensajes irreprocesables quedan visibles en la DLQ en lugar de perderse.
- Reentregas y duplicados no crean incidentes duplicados (inbox).
- Observabilidad: consola de administración de RabbitMQ + logs `Published outbox event ...` + estado del outbox.

**Negativas:**
- Un componente de infraestructura más que hay que levantar y monitorear.
- Complejidad añadida: topología declarada, filtro de propiedad del outbox y tabla de inbox.
- La ventana entre el commit de la base y el `ack` AMQP hace la entrega *at-least-once*; sin el inbox esto introduciría duplicados.
- `backend-shared` deja de drenar eventos de `telemetry`: cada servicio es responsable de publicar lo que genera.

## Alternativas consideradas

- **`ApplicationEventPublisher` in-process (estado previo):** rechazado porque en microservicios no cruza contenedores y pierde eventos si el consumidor cae.
- **Publicación directa tras el commit (`TransactionSynchronization.afterCommit`):** rechazado porque un fallo del broker en ese instante pierde el evento para siempre; el outbox ya era el mecanismo de ADR-002 y reutiliza el despachador y su tabla.
- **Kafka:** rechazado por mayor complejidad operativa sin necesidad de retención/history de mensajes.
- **Requeue infinito ante errores:** rechazado porque un mensaje corrupto ocuparía la cola para siempre; se opta por reintento acotado + DLQ.
