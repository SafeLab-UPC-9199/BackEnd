package com.test.backend.shared.infrastructure.persistence.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mqtt")
@Getter
@Setter
public class MqttProperties {
    private String host;
    private int port;
    private String protocol;
    private String username;
    private String password;
    private String topic;
    private Ssl ssl = new Ssl();

    /**
     * Mutual TLS (X.509) settings for {@code mqtt.protocol=mqtts|ssl}.
     * When neither key-store nor trust-store is configured the client keeps
     * the JVM default SSL socket factory (server-side TLS only).
     */
    @Getter
    @Setter
    public static class Ssl {
        /** Path to the client keystore holding the X.509 client certificate (classpath: or file:). */
        private String keyStore;
        private String keyStorePassword;
        private String keyStoreType = "PKCS12";
        /** Path to the trust store pinning the broker/CA certificate (classpath: or file:). */
        private String trustStore;
        private String trustStorePassword;
        private String trustStoreType = "JKS";

        public boolean isClientCertConfigured() {
            return keyStore != null && !keyStore.isBlank();
        }

        public boolean isTrustStoreConfigured() {
            return trustStore != null && !trustStore.isBlank();
        }
    }
}
