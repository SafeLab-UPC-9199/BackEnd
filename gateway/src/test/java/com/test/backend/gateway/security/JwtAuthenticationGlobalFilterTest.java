package com.test.backend.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationGlobalFilterTest {

    private static final String SECRET =
            "TestSecretKeyForJwtTokenGeneration2026SecureKey32BytesLong!";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtAuthenticationGlobalFilter filter;
    private final AtomicBoolean chainCalled = new AtomicBoolean(false);
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationGlobalFilter(SECRET);
        chainCalled.set(false);
        forwarded.set(null);
    }

    private GatewayFilterChain recordingChain() {
        return exchange -> {
            chainCalled.set(true);
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private String token(String subject, List<String> roles, Long workspaceId) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(KEY, Jwts.SIG.HS256);
        if (roles != null) {
            builder.claim("roles", roles);
        }
        if (workspaceId != null) {
            builder.claim("workspaceId", workspaceId);
        }
        return builder.compact();
    }

    @Test
    void publicPathSkipsValidation() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/authentication/sign-in"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get(), "public path must reach the target service");
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void systemStatusStaysPublic() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/system/status"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void telemetryIngestionStaysPublicForDevices() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/telemetry/ingestion")
                        .header("X-Api-Key", "device-key"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get(), "devices authenticate with X-Api-Key, not a JWT");
    }

    @Test
    void protectedPathWithoutTokenReturns401() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/laboratories"));

        filter.filter(exchange, recordingChain()).block();

        assertFalse(chainCalled.get(), "unauthenticated request must not reach the service");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void malformedBearerTokenReturns401() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidents")
                        .header("Authorization", "Bearer not-a-jwt"));

        filter.filter(exchange, recordingChain()).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void tokenSignedWithAnotherSecretReturns401() {
        var foreignKey = Keys.hmacShaKeyFor("AnotherSecretKeyForJwtGeneration32Bytes!!!!".getBytes(StandardCharsets.UTF_8));
        var foreignToken = Jwts.builder()
                .subject("attacker@upc.edu.pe")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(foreignKey, Jwts.SIG.HS256)
                .compact();
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + foreignToken));

        filter.filter(exchange, recordingChain()).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void validTokenInjectsVerifiedIdentityAndStripsSpoofedHeaders() {
        var bearer = "Bearer " + token(
                "u202312510@upc.edu.pe", List.of("ROLE_ADMIN", "ROLE_USER"), 5L);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidents")
                        .header("Authorization", bearer)
                        // hostile values the client tried to forge
                        .header("X-User-Email", "evil@attacker.example")
                        .header("X-Workspace-Id", "999")
                        .header("X-Actor", "root"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get(), "valid token must reach the service");
        var headers = forwarded.get().getRequest().getHeaders();
        assertEquals("u202312510@upc.edu.pe", headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_USER_EMAIL));
        assertEquals("5", headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_WORKSPACE_ID));
        assertEquals("ROLE_ADMIN,ROLE_USER", headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_USER_ROLES));
        assertNull(headers.getFirst("X-Actor"), "spoofable identity headers must be removed");
        assertEquals(bearer, headers.getFirst("Authorization"),
                "Authorization is forwarded to backend-iam");
    }

    @Test
    void tokenWithoutWorkspaceClaimOmitsHeader() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/laboratories")
                        .header("Authorization", "Bearer " + token("user@upc.edu.pe", List.of("ROLE_USER"), null))
                        .header("X-Workspace-Id", "777"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get());
        var headers = forwarded.get().getRequest().getHeaders();
        assertNull(headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_WORKSPACE_ID),
                "client-saved workspace must not survive when the token carries no claim");
        assertNotNull(headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_USER_EMAIL));
    }

    @Test
    void corsPreflightPassesWithoutToken() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/laboratories"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get(), "preflight must not be rejected by JWT validation");
    }
}
