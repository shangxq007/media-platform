package com.example.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates legacy platform HMAC JWTs (dev endpoint / migration) for Resource Server pipeline.
 */
public class LegacyHmacJwtDecoder implements org.springframework.security.oauth2.jwt.JwtDecoder {

    private final JwtProperties jwtProperties;

    public LegacyHmacJwtDecoder(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public Jwt decode(String token) throws org.springframework.security.oauth2.jwt.JwtException {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Instant issuedAt = claims.getIssuedAt() != null
                    ? claims.getIssuedAt().toInstant()
                    : Instant.now();
            Instant expiresAt = claims.getExpiration() != null
                    ? claims.getExpiration().toInstant()
                    : issuedAt.plusSeconds(3600);

            var builder = Jwt.withTokenValue(token)
                    .header("alg", "HS256")
                    .subject(claims.getSubject())
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt);
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                String claimName = entry.getKey();
                if ("sub".equals(claimName) || "iss".equals(claimName) || "iat".equals(claimName) || "exp".equals(claimName)) {
                    continue;
                }
                builder.claim(claimName, entry.getValue());
            }
            return builder.build();
        } catch (JwtException ex) {
            throw new org.springframework.security.oauth2.jwt.JwtException("Invalid legacy HMAC JWT", ex);
        }
    }
}
