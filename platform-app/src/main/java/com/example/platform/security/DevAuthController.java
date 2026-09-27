package com.example.platform.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Issues a dev JWT for local frontend against {@code bootRun}.
 *
 * <p>DEV-ONLY. AUTH-UNPROTECTED-FIX-002 requires two independent gates:</p>
 *
 * <ol>
 *   <li>{@code app.security.dev-auth-endpoint=true} (default false, property insurance) <b>and</b>
 *       {@link DevAuthSecretGuard#NOT_PRODUCTION_PROFILES} (profile insurance) — the bean does not
 *       exist in {@code prod} / {@code safe-mode} / {@code oidc}, or when the property is off.</li>
 *   <li>A matching {@value DevAuthSecretGuard#HEADER} header carrying
 *       {@code app.security.dev-auth-secret} (env {@code DEV_AUTH_SECRET}); the controller refuses
 *       to mint a token without it, and fails closed when no secret is configured.</li>
 * </ol>
 *
 * <p>The "tenant-1" default is a development convenience and is only reachable with a valid dev
 * secret in a non-production profile.</p>
 */
@RestController
@RequestMapping("/api/dev/auth")
@ConditionalOnProperty(name = "app.security.dev-auth-endpoint", havingValue = "true", matchIfMissing = false)
@Profile(DevAuthSecretGuard.NOT_PRODUCTION_PROFILES)
public class DevAuthController {

    private static final Logger log = LoggerFactory.getLogger(DevAuthController.class);

    private final JwtProperties jwtProperties;
    private final DevAuthSecretGuard secretGuard;

    public DevAuthController(JwtProperties jwtProperties, DevAuthSecretGuard secretGuard) {
        this.jwtProperties = jwtProperties;
        this.secretGuard = secretGuard;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> issueToken(
            @RequestHeader(name = DevAuthSecretGuard.HEADER, required = false) String devAuthSecret,
            @RequestBody(required = false) DevTokenRequest body) {
        String tenantId = body != null && body.tenantId() != null ? body.tenantId() : "tenant-1";
        String userId = body != null && body.userId() != null ? body.userId() : "user-1";

        // DEV-ONLY fail-closed gate: the dev secret is mandatory even in a dev profile, and a
        // missing/incorrect secret is answered exactly like an unavailable endpoint.
        try {
            secretGuard.requireAuthorized(devAuthSecret);
        } catch (RuntimeException denied) {
            audit("DENIED", tenantId, userId);
            throw denied;
        }
        audit("ISSUED", tenantId, userId);

        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(userId)
                .claim("tenantId", tenantId)
                .claim("roles", List.of("USER", "ADMIN"))
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

    /**
     * AUTH-UNPROTECTED-FIX-002 (1.4): every dev token issuance/denial is recorded with time, the
     * requested tenant/user, the caller address and the result. The dev secret itself is never
     * logged.
     */
    private void audit(String result, String tenantId, String userId) {
        log.info("dev-auth action=issueToken result={} tenant={} user={} remote={} at={}",
                result, tenantId, userId, remoteAddress(), Instant.now());
    }

    private static String remoteAddress() {
        try {
            var attributes = RequestContextHolder.currentRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                return servletAttributes.getRequest().getRemoteAddr();
            }
        } catch (RuntimeException outsideRequest) {
            // Unit-level invocation without a servlet request.
        }
        return "unknown";
    }

    public record DevTokenRequest(String tenantId, String userId) {}
}
