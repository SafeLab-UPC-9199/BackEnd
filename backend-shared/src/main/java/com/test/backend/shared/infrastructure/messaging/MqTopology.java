package com.test.backend.shared.infrastructure.messaging;

/**
 * Names of the AMQP topology shared by the producer (backend-telemetry) and the
 * consumer (backend-alerts) of {@code TemperatureReadingRecorded} (ADR-005).
 *
 * <p>Both sides declare the same topology and declaring is idempotent in
 * RabbitMQ, so neither service needs the other to be up before it can start:
 * telemetry keeps buffering readings in the durable queue while alerts is still
 * booting, and alerts can rebuild everything after a broker wipe.
 *
 * <p>This class intentionally lives in backend-shared <b>without</b> any AMQP
 * dependency: only the services that really talk to the broker pull in
 * {@code spring-boot-starter-amqp}, so the other six microservices keep a
 * classpath (and an actuator health) free of RabbitMQ.
 */
public final class MqTopology {

    /** Topic exchange every domain event is published to. */
    public static final String EVENTS_EXCHANGE = "safelab.events";

    /** Direct exchange that receives messages rejected without requeue. */
    public static final String DEAD_LETTER_EXCHANGE = "safelab.events.dlx";

    /** Routing key telemetry uses for every accepted sensor reading. */
    public static final String TEMPERATURE_READING_ROUTING_KEY = "telemetry.temperature.reading.recorded";

    /** Durable queue feeding the alerts consumer. */
    public static final String TEMPERATURE_READING_QUEUE = "alerts.temperature.reading.recorded";

    /** Dead-letter queue parking messages the consumer could not process. */
    public static final String TEMPERATURE_READING_DLQ = "alerts.temperature.reading.recorded.dlq";

    /** Value of {@code SharedEventOutbox.eventType} for this event (ADR-002). */
    public static final String TEMPERATURE_READING_EVENT_TYPE = "TemperatureReadingRecorded";

    /** Queue argument routing a rejected message to the dead-letter exchange. */
    public static final String DLX_ARGUMENT = "x-dead-letter-exchange";

    /** Queue argument overriding the routing key used on dead-lettering. */
    public static final String DLX_ROUTING_KEY_ARGUMENT = "x-dead-letter-routing-key";

    private MqTopology() {
    }
}
