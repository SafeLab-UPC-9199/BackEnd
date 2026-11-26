package com.test.backend.gateway.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final String TOO_MANY_REQUESTS_BODY = "{\"message\":\"Too many requests\"}";

    private final long capacity;
    private final long refillPerMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitingFilter(
            @Value("${gateway.rate-limit.capacity:100}") long capacity,
            @Value("${gateway.rate-limit.refill-per-minute:100}") long refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var path = exchange.getRequest().getURI().getPath();
        if (pathMatcher.match("/actuator/**", path)) {
            return chain.filter(exchange);
        }

        var bucket = buckets.computeIfAbsent(resolveClientIp(exchange), k -> newBucket());
        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }

        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var body = response.bufferFactory().wrap(TOO_MANY_REQUESTS_BODY.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }

    private Bucket newBucket() {
        var limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static String resolveClientIp(ServerWebExchange exchange) {
        var forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
