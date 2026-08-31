package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

/**
 * Tests that DevAuthController has proper conditional annotations.
 */
class DevAuthControllerTest {

    @Test
    void devAuthControllerRequiresStrictNonProductionProfile() {
        Profile profile = DevAuthController.class.getAnnotation(Profile.class);
        assertNotNull(profile, "DevAuthController must have @Profile");
        assertArrayEquals(new String[] {"!prod & (dev | local | test)"}, profile.value());
    }

    @Test
    void devAuthControllerRequiresExplicitEnableAndDisabledProductionChecks() {
        ConditionalOnProperties conditions =
                DevAuthController.class.getAnnotation(ConditionalOnProperties.class);
        assertNotNull(conditions, "DevAuthController must declare both property conditions");
        assertEquals(2, conditions.value().length);

        ConditionalOnProperty endpoint = condition(conditions, "app.security.dev-auth-endpoint");
        assertEquals("true", endpoint.havingValue());
        assertFalse(endpoint.matchIfMissing(), "the issuer must be opt-in");

        ConditionalOnProperty productionChecks =
                condition(conditions, "platform.runtime.production-checks-enabled");
        assertEquals("false", productionChecks.havingValue());
        assertTrue(productionChecks.matchIfMissing(),
                "ordinary explicit local development may omit production checks");
    }

    @Test
    void productionChecksPreventDevLocalAndTestIssuerBeans() {
        for (String profile : Arrays.asList("dev", "local", "test")) {
            context(profile, true).run(applicationContext ->
                    assertFalse(applicationContext.containsBean("devAuthController"),
                            "production checks must leave zero issuer active paths for " + profile));
        }
    }

    @Test
    void prodProfilePreventsIssuerEvenWhenLocalOrTestIsAlsoActive() {
        for (String profiles : Arrays.asList("prod,local", "prod,test")) {
            context(profiles, false).run(applicationContext ->
                    assertFalse(applicationContext.containsBean("devAuthController"),
                            "prod must exclude the issuer for " + profiles));
        }
    }

    @Test
    void explicitLocalIssuerRemainsAvailableWhenProductionChecksAreDisabled() {
        context("local", false).run(applicationContext ->
                assertTrue(applicationContext.containsBean("devAuthController")));
    }

    @Test
    void devAuthControllerRequestMappingIsDevPath() {
        org.springframework.web.bind.annotation.RequestMapping rm =
                DevAuthController.class.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(rm, "DevAuthController must have @RequestMapping");
        String path = rm.value()[0];
        assertTrue(path.contains("/dev/"), "DevAuthController path should contain /dev/: " + path);
    }

    private static ConditionalOnProperty condition(
            ConditionalOnProperties conditions, String propertyName) {
        return Arrays.stream(conditions.value())
                .filter(condition -> Arrays.asList(condition.name()).contains(propertyName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing condition for " + propertyName));
    }

    private static ApplicationContextRunner context(String profiles, boolean productionChecks) {
        return new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=" + profiles,
                        "app.security.dev-auth-endpoint=true",
                        "platform.runtime.production-checks-enabled=" + productionChecks)
                .withBean(JwtProperties.class,
                        () -> new JwtProperties(
                                "test-only-secret-key-at-least-256-bits-long!!", 3600000))
                .withUserConfiguration(DevAuthController.class);
    }
}
