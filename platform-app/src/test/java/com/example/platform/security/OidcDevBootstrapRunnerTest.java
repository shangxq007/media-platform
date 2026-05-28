package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;

/**
 * Tests that OidcDevBootstrapRunner has proper profile and property conditions.
 */
class OidcDevBootstrapRunnerTest {

    @Test
    void oidcDevBootstrapRunnerHasDevProfileOnly() {
        Profile profile = OidcDevBootstrapRunner.class.getAnnotation(Profile.class);
        assertNotNull(profile, "OidcDevBootstrapRunner must have @Profile");
        String[] profiles = profile.value();
        assertTrue(Arrays.asList(profiles).contains("dev"),
                "OidcDevBootstrapRunner must require dev profile");
        assertTrue(Arrays.asList(profiles).contains("local")
                || Arrays.asList(profiles).contains("test"),
                "OidcDevBootstrapRunner must require local or test profile");
        assertFalse(Arrays.asList(profiles).contains("oidc"),
                "OidcDevBootstrapRunner must NOT be triggered by oidc profile alone");
        assertFalse(Arrays.asList(profiles).contains("prod"),
                "OidcDevBootstrapRunner must NOT be triggered by prod profile");
    }

    @Test
    void oidcDevBootstrapRunnerHasConditionalOnProperty() {
        ConditionalOnProperty annotation = OidcDevBootstrapRunner.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(annotation, "OidcDevBootstrapRunner must have @ConditionalOnProperty");
        assertEquals("app.security.oidc-dev-bootstrap.enabled", annotation.name()[0]);
        assertEquals("true", annotation.havingValue());
        assertFalse(annotation.matchIfMissing(),
                "OidcDevBootstrapRunner must NOT match if missing (default=false)");
    }

    @Test
    void oidcDevBootstrapRunnerNoLongerRequiresOauth2Enabled() {
        ConditionalOnProperty annotation = OidcDevBootstrapRunner.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(annotation);
        // Should use oidc-dev-bootstrap.enabled, not oauth2.enabled
        assertFalse(annotation.name()[0].contains("oauth2"),
                "OidcDevBootstrapRunner should not depend on oauth2.enabled");
    }
}
