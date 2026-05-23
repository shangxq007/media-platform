package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class LegacyHmacJwtDecoderTest {

    private final JwtProperties props =
            new JwtProperties("test-secret-key-that-is-at-least-256-bits-long-for-hmac!", 3600000);
    private final LegacyHmacJwtDecoder decoder = new LegacyHmacJwtDecoder(props);

    @Test
    void decodesValidLegacyToken() {
        SecretKey key = Keys.hmacShaKeyFor(props.secretKey().getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-1")
                .claim("tenantId", "tenant-1")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        Jwt jwt = decoder.decode(token);
        assertEquals("user-1", jwt.getSubject());
        assertEquals("tenant-1", jwt.getClaimAsString("tenantId"));
    }

    @Test
    void rejectsInvalidToken() {
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class, () -> decoder.decode("not-a-jwt"));
    }
}
