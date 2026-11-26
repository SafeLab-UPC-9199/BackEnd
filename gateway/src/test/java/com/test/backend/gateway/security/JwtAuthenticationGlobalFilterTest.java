package com.test.backend.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URI;
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
        return token(JwtAuthenticationGlobalFilter.EXPECTED_ISSUER, subject, roles, workspaceId, null, null);
    }

    private String token(
            String issuer,
            String subject,
            List<String> roles,
            Long workspaceId,
            String jti,
            String sessionId) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(KEY, Jwts.SIG.HS256);
        if (issuer != null) {
            builder.issuer(issuer);
        }
        if (roles != null) {
            builder.claim("roles", roles);
        }
        if (workspaceId != null) {
            builder.claim("workspaceId", workspaceId);
        }
        if (jti != null) {
            builder.id(jti);
        }
        if (sessionId != null) {
            builder.claim("sessionId", sessionId);
        }
        return builder.compact();
    }

    @Test
    void publicPathSkipsValidation() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/authentication/sign-in"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get());
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

        assertTrue(chainCalled.get());
    }

    @Test
    void protectedPathWithoutTokenReturns401() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/laboratories"));

        filter.filter(exchange, recordingChain()).block();

        assertFalse(chainCalled.get());
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
                .issuer(JwtAuthenticationGlobalFilter.EXPECTED_ISSUER)
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
    void tokenWithoutIssuerReturns401() {
        var jwt = token(null, "u202312510@upc.edu.pe", List.of("ROLE_USER"), 5L, null, null);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + jwt));

        filter.filter(exchange, recordingChain()).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void tokenWithWrongIssuerReturns401() {
        var jwt = token("other-issuer", "u202312510@upc.edu.pe", List.of("ROLE_USER"), 5L, null, null);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + jwt));

        filter.filter(exchange, recordingChain()).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void validTokenInjectsVerifiedIdentityAndStripsSpoofedHeaders() {
        var bearer = "Bearer " + token(
                JwtAuthenticationGlobalFilter.EXPECTED_ISSUER,
                "u202312510@upc.edu.pe",
                List.of("ROLE_ADMIN", "ROLE_USER"),
                5L,
                "sess-jti-123",
                null);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidents")
                        .header("Authorization", bearer)
                        .header("X-User-Email", "evil@attacker.example")
                        .header("X-Workspace-Id", "999")
                        .header("X-Session-Id", "spoofed-session")
                        .header("X-Actor", "root"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get());
        var headers = forwarded.get().getRequest().getHeaders();
        assertEquals("u202312510@upc.edu.pe", headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_USER_EMAIL));
        assertEquals("5", headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_WORKSPACE_ID));
        assertEquals("ROLE_ADMIN,ROLE_USER", headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_USER_ROLES));
        assertEquals("sess-jti-123", headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_SESSION_ID));
        assertNull(headers.getFirst("X-Actor"));
        assertEquals(bearer, headers.getFirst("Authorization"));
    }

    @Test
    void tokenWithoutWorkspaceOrSessionClaimStripsSpoofedHeaders() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/laboratories")
                        .header("Authorization", "Bearer " + token("user@upc.edu.pe", List.of("ROLE_USER"), null))
                        .header("X-Workspace-Id", "777")
                        .header("X-Session-Id", "spoofed-session"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get());
        var headers = forwarded.get().getRequest().getHeaders();
        assertNull(headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_WORKSPACE_ID));
        assertNull(headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_SESSION_ID));
        assertNotNull(headers.getFirst(JwtAuthenticationGlobalFilter.HEADER_USER_EMAIL));
    }

    @Test
    void sessionCheckEnabledAllowsActiveSessionAndPropagatesSessionId() {
        var calledUri = new AtomicReference<URI>();
        ExchangeFunction exchangeFn = request -> {
            calledUri.set(request.url());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"active\":true}")
                    .build());
        };
        var checkedFilter = new JwtAuthenticationGlobalFilter(
                SECRET, "http://iam-test:8084", true, WebClient.builder().exchangeFunction(exchangeFn).build());

        var jwt = token(JwtAuthenticationGlobalFilter.EXPECTED_ISSUER,
                "user@upc.edu.pe", List.of("ROLE_USER"), 1L, null, "sess-abc");
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/security/status")
                        .header("Authorization", "Bearer " + jwt));

        checkedFilter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get());
        assertEquals("http://iam-test:8084/api/v1/security/sessions/sess-abc/active",
                calledUri.get().toString());
        assertEquals("sess-abc", forwarded.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationGlobalFilter.HEADER_SESSION_ID));
    }

    @Test
    void sessionCheckEnabledRejectsRevokedSessionWith401() {
        ExchangeFunction exchangeFn = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"active\":false}")
                .build());
        var checkedFilter = new JwtAuthenticationGlobalFilter(
                SECRET, "http://iam-test:8084", true, WebClient.builder().exchangeFunction(exchangeFn).build());

        var jwt = token(JwtAuthenticationGlobalFilter.EXPECTED_ISSUER,
                "user@upc.edu.pe", List.of("ROLE_USER"), 1L, "sess-revoked", null);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/laboratories")
                        .header("Authorization", "Bearer " + jwt));

        checkedFilter.filter(exchange, recordingChain()).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void corsPreflightPassesWithoutToken() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/laboratories"));

        filter.filter(exchange, recordingChain()).block();

        assertTrue(chainCalled.get());
    }
}
