package com.test.backend.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationGlobalFilter.class);

    static final String EXPECTED_ISSUER = "safelab-iam";

    static final List<String> PUBLIC_PATH_PATTERNS = List.of(
            "/api/v1/authentication/sign-in",
            "/api/v1/authentication/sign-up",
            "/api/v1/authentication/confirm-account",
            "/api/v1/authentication/resend-code",
            "/api/v1/auth/**",
            "/api/v1/system/status",
            "/api/v1/telemetry/ingestion",
            "/actuator/**",
            "/favicon.ico"
    );

    static final List<String> SPOOFABLE_IDENTITY_HEADERS = List.of(
            "X-User-Email",
            "X-User-Id",
            "X-User-Roles",
            "X-Workspace-Id",
            "X-Session-Id",
            "X-Actor"
    );

    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_WORKSPACE_ID = "X-Workspace-Id";
    public static final String HEADER_SESSION_ID = "X-Session-Id";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED_BODY =
            "{\"message\":\"Missing or invalid access token\"}";

    private final SecretKey signingKey;
    private final String iamBaseUrl;
    private final boolean sessionCheckEnabled;
    private final WebClient webClient;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationGlobalFilter(String secret) {
        this(secret, "http://safelab-backend-iam:8084", false, WebClient.builder().build());
    }

    @Autowired
    public JwtAuthenticationGlobalFilter(
            @Value("${authorization.jwt.secret}") String secret,
            @Value("${gateway.iam.base-url:http://safelab-backend-iam:8084}") String iamBaseUrl,
            @Value("${gateway.session-check.enabled:true}") boolean sessionCheckEnabled,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        this(secret, iamBaseUrl, sessionCheckEnabled,
                webClientBuilderProvider.getIfAvailable(WebClient::builder).build());
    }

    JwtAuthenticationGlobalFilter(
            String secret,
            String iamBaseUrl,
            boolean sessionCheckEnabled,
            WebClient webClient) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.iamBaseUrl = iamBaseUrl;
        this.sessionCheckEnabled = sessionCheckEnabled;
        this.webClient = webClient;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getURI().getPath();

        if (request.getMethod() == HttpMethod.OPTIONS || isPublic(path)) {
            return chain.filter(exchange);
        }

        Claims claims = parseClaims(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (claims == null) {
            return unauthorized(exchange);
        }

        String sessionId = extractSessionId(claims);
        return isSessionActive(sessionId).flatMap(active -> {
            if (!active) {
                return unauthorized(exchange);
            }
            return chain.filter(exchange.mutate()
                    .request(withVerifiedIdentity(request, claims, sessionId))
                    .build());
        });
    }

    private boolean isPublic(String path) {
        for (var pattern : PUBLIC_PATH_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
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
                    .requireIssuer(EXPECTED_ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Rejected JWT at the gateway: {}", e.getMessage());
            return null;
        }
    }

    private static String extractSessionId(Claims claims) {
        var sessionId = claims.get("sessionId", String.class);
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        var jti = claims.getId();
        return (jti != null && !jti.isBlank()) ? jti : null;
    }

    private Mono<Boolean> isSessionActive(String sessionId) {
        if (!sessionCheckEnabled || sessionId == null) {
            return Mono.just(true);
        }
        return webClient.get()
                .uri(iamBaseUrl + "/api/v1/security/sessions/{sessionId}/active", sessionId)
                .retrieve()
                .bodyToMono(SessionActiveResponse.class)
                .map(SessionActiveResponse::active)
                .onErrorResume(e -> {
                    logger.warn("Session active check failed for {}: {}", sessionId, e.getMessage());
                    return Mono.just(false);
                });
    }

    private ServerHttpRequest withVerifiedIdentity(
            ServerHttpRequest request, Claims claims, String sessionId) {
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
            if (sessionId != null) {
                headers.set(HEADER_SESSION_ID, sessionId);
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

    private record SessionActiveResponse(boolean active) {}
}
