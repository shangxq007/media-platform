package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Method;

/**
 * Tests that DevAuthController has proper conditional annotations.
 */
class DevAuthControllerTest {

    @Test
    void devAuthControllerHasConditionalOnProperty() {
        ConditionalOnProperty annotation = DevAuthController.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(annotation, "DevAuthController must have @ConditionalOnProperty");
        assertEquals("app.security.dev-auth-endpoint", annotation.name()[0]);
        assertEquals("true", annotation.havingValue());
        assertFalse(annotation.matchIfMissing(), "DevAuthController must NOT match if missing (default=false)");
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
        DevAuthController controller = new DevAuthController(properties);

        String token = (String) controller.issueToken(
                        new DevAuthController.DevTokenRequest("tenant-1", "user-1"))
                .getBody().get("accessToken");
        var key = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertEquals(List.of("USER", "ADMIN"), claims.get("roles", List.class));
    }
}
