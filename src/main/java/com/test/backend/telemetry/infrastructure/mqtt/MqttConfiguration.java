package com.test.backend.telemetry.infrastructure.mqtt;

import com.test.backend.shared.infrastructure.persistence.configuration.MqttProperties;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.UUID;

/**
 * MQTT v5 client configuration.
 *
 * Protocol is configurable through {@code mqtt.protocol}:
 * - {@code mqtts}/{@code ssl} → TLS (ssl://host:port)
 * - {@code tcp} (default)     → plain text
 *
 * Mutual authentication (mTLS, X.509 client certificate, TLS 1.3 — QAS-01):
 * configure {@code mqtt.ssl.key-store} (client cert) and optionally
 * {@code mqtt.ssl.trust-store} (pinned broker/CA cert). Without them the
 * JVM default trust store is used (server-side TLS only).
 */
@Configuration
public class MqttConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MqttConfiguration.class);

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
        if (isTls(protocol)) {
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

        var ssl = mqttProperties.getSsl();
        boolean tlsConfigured = ssl != null && (ssl.isClientCertConfigured() || ssl.isTrustStoreConfigured());

        if (isTls(protocol)) {
            options.setSocketFactory(createSocketFactory());
        } else if (tlsConfigured) {
            logger.warn("mqtt.ssl keystores are configured but mqtt.protocol='{}' is not TLS — "
                    + "mutual authentication (mTLS) is NOT active. Set mqtt.protocol=mqtts.", protocol);
        }
        return options;
    }

    private boolean isTls(String protocol) {
        return protocol != null && (protocol.equalsIgnoreCase("mqtts") || protocol.equalsIgnoreCase("ssl"));
    }

    /**
     * Builds the SSL socket factory for the TLS transport:
     * - with {@code mqtt.ssl.*} keystores → mTLS on TLS 1.3 (client X.509 cert + pinned trust store)
     * - without them → JVM default (server-side TLS, default trust store)
     */
    private SSLSocketFactory createSocketFactory() {
        var ssl = mqttProperties.getSsl();
        if (ssl == null || (!ssl.isClientCertConfigured() && !ssl.isTrustStoreConfigured())) {
            logger.info("MQTT TLS without client certificate (mTLS not configured) — using default trust store.");
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
        try {
            SSLContext context = SSLContext.getInstance("TLSv1.3");

            KeyManagerFactory keyManagerFactory = null;
            if (ssl.isClientCertConfigured()) {
                KeyStore keyStore = KeyStore.getInstance(ssl.getKeyStoreType());
                try (InputStream in = openStore(ssl.getKeyStore())) {
                    keyStore.load(in, chars(ssl.getKeyStorePassword()));
                }
                keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, chars(ssl.getKeyStorePassword()));
                logger.info("MQTT mTLS: client certificate loaded from {} (type {}).",
                        ssl.getKeyStore(), ssl.getKeyStoreType());
            }

            TrustManagerFactory trustManagerFactory = null;
            if (ssl.isTrustStoreConfigured()) {
                KeyStore trustStore = KeyStore.getInstance(ssl.getTrustStoreType());
                try (InputStream in = openStore(ssl.getTrustStore())) {
                    trustStore.load(in, chars(ssl.getTrustStorePassword()));
                }
                trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                logger.info("MQTT mTLS: pinned trust store loaded from {} (type {}).",
                        ssl.getTrustStore(), ssl.getTrustStoreType());
            }

            context.init(
                    keyManagerFactory != null ? keyManagerFactory.getKeyManagers() : null,
                    trustManagerFactory != null ? trustManagerFactory.getTrustManagers() : null,
                    null);
            return context.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure MQTT mutual TLS (X.509): " + e.getMessage(), e);
        }
    }

    private InputStream openStore(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            var resource = new org.springframework.core.io.ClassPathResource(path.substring("classpath:".length()));
            return resource.getInputStream();
        }
        String filePath = path.startsWith("file:") ? path.substring("file:".length()) : path;
        return new FileInputStream(new File(filePath));
    }

    private static char[] chars(String password) {
        return password != null ? password.toCharArray() : new char[0];
    }
}
