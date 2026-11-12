package com.test.backend.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Edge authentication filter (Fase 6, ADR-006).
 *
 * <p>Validates the Bearer JWT locally with the shared HMAC secret (the same
 * {@code authorization.jwt.secret} and HS256 algorithm used by backend-iam),
 * then strips any identity header sent by the client and injects the verified
 * claims as {@code X-User-Email}, {@code X-User-Roles} and
 * {@code X-Workspace-Id}. Downstream services trust those headers: they are
 * only trustworthy because everything reachable through this filter had its
 * client-supplied copy removed first.</p>
 *
 * <p>Public paths (login, telemetry ingestion with its own X-Api-Key, system
 * status, gateway actuator) skip validation so machine-to-machine and anonymous
 * flows keep working.</p>
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationGlobalFilter.class);

    /** Path pattern (Ant-style) of endpoints reachable without a JWT. */
    static final List<String> PUBLIC_PATH_PATTERNS = List.of(
            "/api/v1/authentication/sign-in",
            "/api/v1/authentication/sign-up",
            "/api/v1/authentication/confirm-account",
            "/api/v1/authentication/resend-code",
            "/api/v1/auth/**",                      // legacy alias of the auth surface
            "/api/v1/system/status",
            "/api/v1/telemetry/ingestion",          // devices authenticate with X-Api-Key
            "/actuator/**",
            "/favicon.ico"
    );

    /** Identity headers a client may try to forge; always removed. */
    static final List<String> SPOOFABLE_IDENTITY_HEADERS = List.of(
            "X-User-Email",
            "X-User-Id",
            "X-User-Roles",
            "X-Workspace-Id",
            "X-Actor"
    );

    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_WORKSPACE_ID = "X-Workspace-Id";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED_BODY =
            "{\"message\":\"Missing or invalid access token\"}";

    private final SecretKey signingKey;

    public JwtAuthenticationGlobalFilter(
            @Value("${authorization.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int getOrder() {
        // Run before route resolution: an unauthenticated request must not even
        // reach the target service.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getURI().getPath();

        // CORS preflight carries no Authorization header; the CORS
        // configuration already constrains origins/methods/headers.
        if (request.getMethod() == HttpMethod.OPTIONS || isPublic(path)) {
            return chain.filter(exchange);
        }

        Claims claims = parseClaims(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (claims == null) {
            return unauthorized(exchange);
        }

        return chain.filter(exchange.mutate().request(withVerifiedIdentity(request, claims)).build());
    }

    private boolean isPublic(String path) {
        var matcher = new AntPathMatcher();
        for (var pattern : PUBLIC_PATH_PATTERNS) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Claims parseClaims(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        var token = authorizationHeader.substring(BEARER_PREFIX.length());
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Rejected JWT at the gateway: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Rebuilds the request with every client-supplied identity header removed
     * and the verified claims injected. Authorization is forwarded unchanged so
     * backend-iam keeps validating its own protected endpoints.
     */
    private ServerHttpRequest withVerifiedIdentity(ServerHttpRequest request, Claims claims) {
        return request.mutate().headers(headers -> {
            SPOOFABLE_IDENTITY_HEADERS.forEach(headers::remove);

            if (claims.getSubject() != null) {
                headers.set(HEADER_USER_EMAIL, claims.getSubject());
            }
            var roles = claims.get("roles", List.class);
            if (roles != null) {
                headers.set(HEADER_USER_ROLES, String.join(",", roles.stream()
                        .map(Object::toString).toList()));
            }
            var workspaceId = claims.get("workspaceId");
            if (workspaceId instanceof Number number) {
                headers.set(HEADER_WORKSPACE_ID, String.valueOf(number.longValue()));
            }
        }).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var body = response.bufferFactory().wrap(UNAUTHORIZED_BODY.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }
}
