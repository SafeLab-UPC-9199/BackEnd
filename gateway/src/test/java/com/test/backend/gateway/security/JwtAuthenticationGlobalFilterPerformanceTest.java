package com.test.backend.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Micro-benchmark of the edge JWT validation (Fase 7, ADR-006).
 *
 * <p>Quantifies the "cero latencia añadida por autenticación" claim: HMAC-SHA256
 * verification is CPU-bound and must stay in the microsecond range. Thresholds
 * are deliberately generous (they only catch order-of-magnitude regressions,
 * such as accidentally reintroducing a remote call per request) so the test is
 * stable on shared machines.</p>
 *
 * <p>Tagged {@code performance}: excluded from the default build
 * ({@code test.excluded.groups=it,performance}); run explicitly with
 * {@code mvn test -Dtest.excluded.groups=}.</p>
 */
@Tag("performance")
class JwtAuthenticationGlobalFilterPerformanceTest {

    private static final String SECRET =
            "TestSecretKeyForJwtTokenGeneration2026SecureKey32BytesLong!";

    private static final int WARMUP = 1_000;
    private static final int MEASURED_ITERATIONS = 10_000;
    /** Generous ceiling: real cost is tens of microseconds per request. */
    private static final double MAX_AVG_MILLIS_PER_REQUEST = 1.0;

    private final JwtAuthenticationGlobalFilter filter =
            new JwtAuthenticationGlobalFilter(SECRET);

    private String validToken() {
        return Jwts.builder()
                .subject("u202312510@upc.edu.pe")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .claim("roles", List.of("ROLE_ADMIN"))
                .claim("workspaceId", 5L)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void validTokenValidationThroughput() {
        var token = validToken();
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + token));
        var forwardCount = new AtomicInteger();
        GatewayFilterChain chain = ex -> {
            forwardCount.incrementAndGet();
            return Mono.empty();
        };

        for (int i = 0; i < WARMUP; i++) {
            filter.filter(exchange, chain).block();
        }
        forwardCount.set(0);

        var started = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            filter.filter(exchange, chain).block();
        }
        var elapsedNanos = System.nanoTime() - started;
        var avgMillis = TimeUnit.NANOSECONDS.toMicros(elapsedNanos) / 1000.0 / MEASURED_ITERATIONS;
        var opsPerSecond = MEASURED_ITERATIONS / (elapsedNanos / 1_000_000_000.0);

        System.out.printf("valid-token path: %d requests in %d ms -> %.0f req/s, avg %.3f ms/req%n",
                MEASURED_ITERATIONS, TimeUnit.NANOSECONDS.toMillis(elapsedNanos), opsPerSecond, avgMillis);

        assertEquals(MEASURED_ITERATIONS, forwardCount.get(), "every valid token must be forwarded");
        assertTrue(avgMillis <= MAX_AVG_MILLIS_PER_REQUEST,
                "avg validation cost " + avgMillis + " ms/req exceeds " + MAX_AVG_MILLIS_PER_REQUEST + " ms");
    }

    @Test
    void rejectionThroughputForMissingToken() {
        var request = MockServerHttpRequest.get("/api/v1/laboratories").build();
        var forwardCount = new AtomicInteger();
        GatewayFilterChain chain = ex -> {
            forwardCount.incrementAndGet();
            return Mono.empty();
        };

        // A fresh exchange per call: the 401 path writes and commits the
        // response, exactly as one real request does.
        for (int i = 0; i < WARMUP; i++) {
            filter.filter(MockServerWebExchange.from(request), chain).block();
        }
        forwardCount.set(0);

        var started = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            filter.filter(MockServerWebExchange.from(request), chain).block();
        }
        var elapsedNanos = System.nanoTime() - started;
        var avgMillis = TimeUnit.NANOSECONDS.toMicros(elapsedNanos) / 1000.0 / MEASURED_ITERATIONS;

        System.out.printf("rejection path: %d requests in %d ms -> avg %.3f ms/req%n",
                MEASURED_ITERATIONS, TimeUnit.NANOSECONDS.toMillis(elapsedNanos), avgMillis);

        assertEquals(0, forwardCount.get(), "unauthenticated requests must never be forwarded");
        assertTrue(avgMillis <= MAX_AVG_MILLIS_PER_REQUEST,
                "avg rejection cost " + avgMillis + " ms/req exceeds " + MAX_AVG_MILLIS_PER_REQUEST + " ms");
    }
}
