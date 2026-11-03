package com.test.backend.telemetry.infrastructure.mqtt;

import com.test.backend.shared.infrastructure.persistence.configuration.MqttProperties;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLSocketFactory;
import java.util.UUID;

/**
 * MQTT v5 client configuration.
 *
 * Protocol is configurable through {@code mqtt.protocol}:
 * - {@code mqtts}/{@code ssl} → TLS (ssl://host:port, default trust store)
 * - {@code tcp} (default)     → plain text
 *
 * The v5 client always speaks MQTT 5.0; if a broker only supports 3.1.1 the
 * connection must be pointed at such broker via configuration (no code change).
 */
@Configuration
public class MqttConfiguration {

    private final MqttProperties mqttProperties;

    public MqttConfiguration(MqttProperties mqttProperties) {
        this.mqttProperties = mqttProperties;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
    public MqttClient mqttClient() throws Exception {
        String protocol = mqttProperties.getProtocol();
        if (protocol == null) protocol = "tcp";

        String serverUri;
        if (protocol.equalsIgnoreCase("mqtts") || protocol.equalsIgnoreCase("ssl")) {
            serverUri = "ssl://" + mqttProperties.getHost() + ":" + mqttProperties.getPort();
        } else {
            serverUri = "tcp://" + mqttProperties.getHost() + ":" + mqttProperties.getPort();
        }

        // The v5 client has no generateClientId(); build our own stable-unique id.
        String clientId = "SafeLab_Backend_" + UUID.randomUUID();
        return new MqttClient(serverUri, clientId, new MemoryPersistence());
    }

    @Bean
    @ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
    public MqttConnectionOptions mqttConnectionOptions() {
        String protocol = mqttProperties.getProtocol();
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setAutomaticReconnect(true);
        options.setCleanStart(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        options.setMaxReconnectDelay(5000);

        if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isEmpty()) {
            options.setUserName(mqttProperties.getUsername());
        }
        if (mqttProperties.getPassword() != null && !mqttProperties.getPassword().isEmpty()) {
            options.setPassword(mqttProperties.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        if (protocol != null && (protocol.equalsIgnoreCase("mqtts") || protocol.equalsIgnoreCase("ssl"))) {
            try {
                options.setSocketFactory(javax.net.ssl.SSLSocketFactory.getDefault());
            } catch (Exception ignored) {}
        }
        return options;
    }
}
