package com.test.backend.iam.infrastructure.tokens.jwt.services;

import com.test.backend.iam.infrastructure.tokens.jwt.BearerTokenService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Service
public class TokenServiceImpl implements BearerTokenService {

    public static final String ISSUER = "safelab-iam";

    private static final Logger logger = LoggerFactory.getLogger(TokenServiceImpl.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${authorization.jwt.secret}")
    private String secret;

    @Value("${authorization.jwt.expiration.minutes:60}")
    private long expirationMinutes;

    @Value("${authorization.jwt.expiration.days:0}")
    private int expirationDays;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateToken(String email) {
        return generateToken(email, List.of(), null, null);
    }

    @Override
    public String generateToken(String email, Collection<String> roles, Long workspaceId) {
        return generateToken(email, roles, workspaceId, null);
    }

    @Override
    public String generateToken(String email, Collection<String> roles, Long workspaceId, String sessionId) {
        var now = new Date();
        long ttlMillis = expirationMinutes > 0
                ? expirationMinutes * 60_000L
                : Math.max(expirationDays, 1) * 24L * 60 * 60 * 1000;
        var expiration = new Date(now.getTime() + ttlMillis);
        var builder = Jwts.builder()
                .issuer(ISSUER)
                .subject(email)
                .issuedAt(now)
                .expiration(expiration);
        if (sessionId != null && !sessionId.isBlank()) {
            builder.id(sessionId);
            builder.claim("sessionId", sessionId);
        }
        if (roles != null && !roles.isEmpty()) {
            builder.claim("roles", List.copyOf(roles));
        }
        if (workspaceId != null) {
            builder.claim("workspaceId", workspaceId);
        }
        return builder
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public String getSessionIdFromToken(String token) {
        try {
            var claims = parseClaims(token);
            return claims.getId() != null ? claims.getId() : claims.get("sessionId", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getBearerTokenFrom(HttpServletRequest request) {
        var header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
