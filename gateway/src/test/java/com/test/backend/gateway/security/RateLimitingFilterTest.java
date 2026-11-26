package com.test.backend.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateLimitingFilterTest {

    @Test
    void returns429WhenClientExceedsCapacity() {
        var filter = new RateLimitingFilter(2, 2);
        var forwarded = new AtomicInteger();
        GatewayFilterChain chain = exchange -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };

        for (int i = 0; i < 2; i++) {
            var ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/laboratories")
                    .header("X-Forwarded-For", "198.51.100.10, 10.0.0.1"));
            filter.filter(ex, chain).block();
            assertNull(ex.getResponse().getStatusCode());
        }

        var blocked = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/laboratories")
                .header("X-Forwarded-For", "198.51.100.10"));
        filter.filter(blocked, chain).block();

        assertEquals(2, forwarded.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getResponse().getStatusCode());

        var otherIp = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/laboratories")
                .remoteAddress(new InetSocketAddress("198.51.100.11", 5000)));
        filter.filter(otherIp, chain).block();

        assertEquals(3, forwarded.get());
        assertNull(otherIp.getResponse().getStatusCode());
    }

    @Test
    void excludesActuatorEndpointsFromRateLimiting() {
        var filter = new RateLimitingFilter(1, 1);
        var forwarded = new AtomicInteger();
        GatewayFilterChain chain = exchange -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };

        for (int i = 0; i < 5; i++) {
            var ex = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health")
                    .header("X-Forwarded-For", "198.51.100.20"));
            filter.filter(ex, chain).block();
            assertNull(ex.getResponse().getStatusCode());
        }

        assertEquals(5, forwarded.get());
    }
}
