package com.example.platform;

import com.example.platform.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;

/**
 * Test-only JWT token factory using the legacy HMAC key.
 * Creates tokens for anonymous, non-admin, and admin identities.
 */
public class JwtTestHelper {

    private final JwtProperties jwtProperties;

    public JwtTestHelper(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String createToken(String userId, String tenantId, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("tenantId", tenantId)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    public String nonAdminToken() {
        return createToken("test-user", "tenant-1", List.of("USER"));
    }

    public String adminToken() {
        return createToken("test-admin", "tenant-1", List.of("ADMIN", "USER"));
    }
}
