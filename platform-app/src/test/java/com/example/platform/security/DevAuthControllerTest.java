package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.*;

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
}
