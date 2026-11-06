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

    @Getter
    @Setter
    public static class Ssl {
        private String keyStore;
        private String keyStorePassword;
        private String keyStoreType = "PKCS12";
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
