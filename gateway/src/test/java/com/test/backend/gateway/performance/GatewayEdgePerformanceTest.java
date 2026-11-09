package com.test.backend.gateway.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Load/latency benchmarks through the edge gateway (Fase 7).
 *
 * <p>Measures the two paths the quality attributes in ADR-004/ADR-006 talk
 * about: authenticated reads behind edge JWT validation, and the critical IoT
 * ingestion path (X-Api-Key, Feign to automation, outbox write). Thresholds
 * are generous on purpose — they catch order-of-magnitude regressions (an
 * accidental remote introspection per request, a serialised dependency call)
 * without flaking on a shared machine.</p>
 *
 * <p><b>Requires the docker compose stack up</b> (and, for ingestion,
 * {@code TELEMETRY_INGESTION_API_KEY}; defaults to the local drill key
 * {@code fase5-test-key}). Tagged {@code performance}: excluded from the
 * default build, run with {@code mvn test -Dtest.excluded.groups=}.</p>
 */
@Tag("performance")
class GatewayEdgePerformanceTest {

    private static final String BASE_URL =
            System.getenv().getOrDefault("GATEWAY_BASE_URL", "http://localhost:8080");
    private static final String INGESTION_API_KEY =
            System.getenv().getOrDefault("TELEMETRY_INGESTION_API_KEY", "fase5-test-key");
    private static final String SENSOR_UNIT = "Lab-04-TEMP-01";

    private static final String ADMIN_EMAIL = "admin@safelab.com";
    private static final String ADMIN_PASSWORD = "Admin123!";

    private static final int READ_ITERATIONS = 200;
    private static final int INGESTION_ITERATIONS = 50;
    private static final double MAX_READ_P95_MILLIS = 750.0;
    private static final double MAX_INGESTION_P95_MILLIS = 1_500.0;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private record TimedResponse(int status, String body, long millis) {}

    private TimedResponse send(String method, String path, List<String[]> headers, String json)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(20));
        if (headers != null) {
            for (var header : headers) {
                builder.header(header[0], header[1]);
            }
        }
        if (json != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        var started = System.nanoTime();
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        var elapsed = (System.nanoTime() - started) / 1_000_000;
        return new TimedResponse(response.statusCode(), response.body(), elapsed);
    }

    /** Ensures the demo admin exists and returns a fresh JWT. */
    private String signIn() throws Exception {
        var signup = "{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD
                + "\",\"fullName\":\"Admin SafeLab\",\"roles\":[\"ROLE_ADMIN\"]}";
        var created = send("POST", "/api/v1/authentication/sign-up", null, signup);
        assertTrue(created.status() == 201 || created.status() == 409,
                "sign-up must succeed or report the user already exists, got "
                        + created.status() + ": " + created.body());

        var signin = "{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}";
        var login = send("POST", "/api/v1/authentication/sign-in", null, signin);
        assertEquals(200, login.status(), "sign-in failed: " + login.body());
        JsonNode node = MAPPER.readTree(login.body());
        var token = node.path("token").asText(null);
        assertTrue(token != null && !token.isBlank(), "login response has no token");
        return token;
    }

    private static double percentile(List<Long> sortedMillis, double p) {
        var index = (int) Math.ceil(p * sortedMillis.size()) - 1;
        return sortedMillis.get(Math.max(0, Math.min(index, sortedMillis.size() - 1)));
    }

    private static void report(String name, int iterations, List<Long> durations, double p95Limit) {
        var sorted = new ArrayList<>(durations);
        sorted.sort(Long::compare);
        var total = durations.stream().mapToLong(Long::longValue).sum();
        var avg = total / (double) durations.size();
        var p50 = percentile(sorted, 0.50);
        var p95 = percentile(sorted, 0.95);
        var throughput = durations.size() / (total / 1000.0);
        System.out.printf(
                "%s: %d requests, avg %.1f ms, p50 %.0f ms, p95 %.0f ms (limit %.0f), %.1f req/s%n",
                name, iterations, avg, p50, p95, p95Limit, throughput);
        assertTrue(p95 <= p95Limit,
                name + " p95=" + p95 + " ms exceeds limit " + p95Limit + " ms");
    }

    @Test
    void authenticatedReadLatencyThroughEdge() throws Exception {
        var token = signIn();
        var auth = new String[][]{{"Authorization", "Bearer " + token}};

        for (int i = 0; i < 5; i++) {
            var warmup = send("GET", "/api/v1/laboratories", List.of(auth), null);
            assertEquals(200, warmup.status(), "warmup read failed: " + warmup.body());
        }

        var durations = new ArrayList<Long>(READ_ITERATIONS);
        for (int i = 0; i < READ_ITERATIONS; i++) {
            var response = send("GET", "/api/v1/laboratories", List.of(auth), null);
            assertEquals(200, response.status(), "read failed: " + response.body());
            durations.add(response.millis());
        }

        report("authenticated GET /laboratories", READ_ITERATIONS, durations, MAX_READ_P95_MILLIS);
    }

    @Test
    void telemetryIngestionThroughput() throws Exception {
        var headers = List.of(new String[][]{{"X-Api-Key", INGESTION_API_KEY}});
        var baseTimestamp = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            var warmup = send("POST", "/api/v1/telemetry/ingestion", headers,
                    ingestionPayload(baseTimestamp - (3 - i) * 1_000L));
            assertEquals(202, warmup.status(), "warmup ingestion failed: " + warmup.body());
        }

        var durations = new ArrayList<Long>(INGESTION_ITERATIONS);
        for (int i = 0; i < INGESTION_ITERATIONS; i++) {
            var response = send("POST", "/api/v1/telemetry/ingestion", headers,
                    ingestionPayload(baseTimestamp + i));
            assertEquals(202, response.status(),
                    "ingestion failed (check TELEMETRY_INGESTION_API_KEY): " + response.body());
            durations.add(response.millis());
        }

        report("POST /telemetry/ingestion", INGESTION_ITERATIONS, durations, MAX_INGESTION_P95_MILLIS);
    }

    private static String ingestionPayload(long timestamp) {
        return "{\"unit\":\"" + SENSOR_UNIT + "\",\"timestamp\":" + timestamp
                + ",\"metrics\":{\"temperature\":5.4}}";
    }
}
