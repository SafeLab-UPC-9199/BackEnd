package com.test.backend.iam.infrastructure.tokens.jwt.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceImplTest {

    private static final String SECRET =
            "SafeLabSecretKeyForJwtTokenGeneration2026SecureKey32BytesLong!";

    private TokenServiceImpl service;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        service = new TokenServiceImpl();
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "expirationDays", 50);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    @Test
    void tokenCarriesRolesAndWorkspaceClaimsForTheGateway() {
        var token = service.generateToken("u202312510@upc.edu.pe", List.of("ROLE_ADMIN", "ROLE_USER"), 5L);
        var claims = parse(token).getPayload();

        assertEquals("u202312510@upc.edu.pe", claims.getSubject());
        assertEquals(List.of("ROLE_ADMIN", "ROLE_USER"), claims.get("roles", List.class));
        assertEquals(5, ((Number) claims.get("workspaceId")).intValue());
        assertTrue(service.validateToken(token));
    }

    @Test
    void legacySignatureOnlyCarriesSubject() {
        var claims = parse(service.generateToken("plain@upc.edu.pe")).getPayload();

        assertEquals("plain@upc.edu.pe", claims.getSubject());
        assertNull(claims.get("roles"), "empty roles must not be serialized");
        assertNull(claims.get("workspaceId"));
    }

    @Test
    void missingWorkspaceClaimWhenCallerHasNone() {
        var claims = parse(service.generateToken("no-profile@upc.edu.pe", List.of("ROLE_USER"), null)).getPayload();

        assertEquals("no-profile@upc.edu.pe", claims.getSubject());
        assertEquals(List.of("ROLE_USER"), claims.get("roles", List.class));
        assertNull(claims.get("workspaceId"));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        var foreignKey = Keys.hmacShaKeyFor("AnotherSecretKeyForJwtGeneration32Bytes!!!!".getBytes(StandardCharsets.UTF_8));
        var forged = Jwts.builder()
                .subject("attacker@upc.edu.pe")
                .signWith(foreignKey, Jwts.SIG.HS256)
                .compact();

        assertFalse(service.validateToken(forged));
    }
}
