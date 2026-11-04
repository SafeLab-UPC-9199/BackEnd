package com.test.backend.shared.infrastructure.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(60, Refill.greedy(60, Duration.ofMinutes(1)));
            return Bucket.builder().addLimit(limit).build();
        });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && (path.equals("/api/v1/telemetry/ingestion") || path.endsWith("/api/v1/telemetry/ingestion"))) {
            String apiKey = request.getHeader("X-Api-Key");
            String key = apiKey != null && !apiKey.isBlank() ? "ingest:" + apiKey : "ingest:ip:" + request.getRemoteAddr();
            Bucket bucket = resolveBucket(key);
            if (bucket.tryConsume(1)) {
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":\"ERROR\",\"message\":\"Rate limit exceeded\"}");
            }
            return;
        }
        filterChain.doFilter(request, response);
    }
}
