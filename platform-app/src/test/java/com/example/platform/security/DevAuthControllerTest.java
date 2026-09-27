package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

/**
 * Tests that DevAuthController has proper conditional annotations.
 */
class DevAuthControllerTest {

    private static final String SECRET = "test-dev-auth-secret";

    @Test
    void devAuthControllerHasConditionalOnProperty() {
        ConditionalOnProperty annotation = DevAuthController.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(annotation, "DevAuthController must have @ConditionalOnProperty");
        assertEquals("app.security.dev-auth-endpoint", annotation.name()[0]);
        assertEquals("true", annotation.havingValue());
        assertFalse(annotation.matchIfMissing(), "DevAuthController must NOT match if missing (default=false)");
    }

    @Test
    void devAuthControllerExcludesProductionProfiles() {
        // AUTH-UNPROTECTED-FIX-002 (1.1): property insurance alone is not enough; the surface must
        // also be excluded from production-like profiles. Removing @Profile must fail this test.
        Profile profile = DevAuthController.class.getAnnotation(Profile.class);
        assertNotNull(profile, "DevAuthController must carry a @Profile production-exclusion guard");
        assertEquals(DevAuthSecretGuard.NOT_PRODUCTION_PROFILES, profile.value()[0]);
        for (String production : List.of("prod", "safe-mode", "oidc")) {
            assertTrue(DevAuthSecretGuard.NOT_PRODUCTION_PROFILES.contains("!" + production),
                    "profile guard must exclude " + production);
        }
    }

    @Test
    void devAuthControllerRequestMappingIsDevPath() {
        org.springframework.web.bind.annotation.RequestMapping rm =
                DevAuthController.class.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(rm, "DevAuthController must have @RequestMapping");
        String path = rm.value()[0];
        assertTrue(path.contains("/dev/"), "DevAuthController path should contain /dev/: " + path);
    }

    @Test
    void issuedTokenUsesListValuedRolesAcceptedByJwtFilter() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-that-is-at-least-256-bits-long-for-hmac!", 3600000);
        DevAuthController controller = new DevAuthController(properties, guard());

        String token = (String) controller.issueToken(
                        SECRET,
                        new DevAuthController.DevTokenRequest("tenant-1", "user-1"))
                .getBody().get("accessToken");
        var key = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertEquals(List.of("USER", "ADMIN"), claims.get("roles", List.class));
    }

    @Test
    void issueTokenFailsClosedWithoutTheDevSecret() {
        // AUTH-UNPROTECTED-FIX-002 (1.2): the controller itself must refuse to mint. Removing the
        // secret check must fail this test.
        JwtProperties properties = new JwtProperties(
                "test-secret-key-that-is-at-least-256-bits-long-for-hmac!", 3600000);
        DevAuthController controller = new DevAuthController(properties, guard());
        DevAuthController.DevTokenRequest body = new DevAuthController.DevTokenRequest("tenant-1", "user-1");

        assertThrows(ResponseStatusException.class, () -> controller.issueToken(null, body));
        assertThrows(ResponseStatusException.class, () -> controller.issueToken("wrong-secret", body));
        assertThrows(ResponseStatusException.class, () -> controller.issueToken(" ", body));
    }

    @Test
    void issueTokenFailsClosedWhenNoSecretIsConfigured() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-that-is-at-least-256-bits-long-for-hmac!", 3600000);
        DevAuthController controller = new DevAuthController(properties, guardWithSecret(""));

        assertThrows(ResponseStatusException.class, () -> controller.issueToken(
                SECRET, new DevAuthController.DevTokenRequest("tenant-1", "user-1")));
    }

    private static DevAuthSecretGuard guard() {
        return guardWithSecret(SECRET);
    }

    private static DevAuthSecretGuard guardWithSecret(String secret) {
        MockEnvironment environment = new MockEnvironment();
        return new DevAuthSecretGuard(environment, true, secret);
    }
}
