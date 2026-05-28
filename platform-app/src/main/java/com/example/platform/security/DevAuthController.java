package com.example.platform.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues a dev JWT for local frontend against {@code bootRun}.
 *
 * <p>DEV-ONLY: This endpoint is gated by {@code app.security.dev-auth-endpoint=true}
 * and is never enabled in production. The "tenant-1" default is a development convenience.
 */
@RestController
@RequestMapping("/api/v1/dev/auth")
@ConditionalOnProperty(name = "app.security.dev-auth-endpoint", havingValue = "true", matchIfMissing = false)
public class DevAuthController {

    private final JwtProperties jwtProperties;

    public DevAuthController(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> issueToken(@RequestBody(required = false) DevTokenRequest body) {
        // DEV-ONLY: This endpoint is gated by @ConditionalOnProperty("app.security.dev-auth-endpoint=true").
        // The "tenant-1" default is intentionally a development convenience and is NEVER used in production.
        String tenantId = body != null && body.tenantId() != null ? body.tenantId() : "tenant-1";
        String userId = body != null && body.userId() != null ? body.userId() : "user-1";

        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(userId)
                .claim("tenantId", tenantId)
                .claim("roles", "USER,ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(jwtProperties.expirationMs())))
                .signWith(key)
                .compact();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", token);
        result.put("tokenType", "Bearer");
        result.put("tenantId", tenantId);
        result.put("userId", userId);
        result.put("expiresInMs", jwtProperties.expirationMs());
        return ResponseEntity.ok(result);
    }

    public record DevTokenRequest(String tenantId, String userId) {}
}
