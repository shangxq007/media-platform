package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;

/**
 * Tests that OidcDevBootstrapRunner has proper profile and property conditions.
 */
class OidcDevBootstrapRunnerTest {

    @Test
    void oidcDevBootstrapRunnerHasStrictNonProductionProfileExpression() {
        Profile profile = OidcDevBootstrapRunner.class.getAnnotation(Profile.class);
        assertNotNull(profile, "OidcDevBootstrapRunner must have @Profile");
        assertArrayEquals(new String[] {"!prod & (dev | local | test)"}, profile.value(),
                "OIDC bootstrap must require a local/test profile and explicitly exclude prod");
    }

    @Test
    void oidcDevBootstrapRunnerRequiresExplicitEnableAndDisabledProductionChecks() {
        ConditionalOnProperties conditions =
                OidcDevBootstrapRunner.class.getAnnotation(ConditionalOnProperties.class);
        assertNotNull(conditions, "OidcDevBootstrapRunner must declare both property conditions");
        assertEquals(2, conditions.value().length);

        ConditionalOnProperty bootstrap = condition(
                conditions, "app.security.oidc-dev-bootstrap.enabled");
        assertEquals("true", bootstrap.havingValue());
        assertFalse(bootstrap.matchIfMissing(), "bootstrap must be opt-in");

        ConditionalOnProperty productionChecks = condition(
                conditions, "platform.runtime.production-checks-enabled");
        assertEquals("false", productionChecks.havingValue());
        assertTrue(productionChecks.matchIfMissing(),
                "ordinary local development may omit production checks");
    }

    @Test
    void productionChecksPreventLocalAndTestBootstrapBeans() {
        for (String profile : Arrays.asList("local", "test")) {
            context(profile, true).run(applicationContext ->
                    assertFalse(applicationContext.containsBean("oidcDevBootstrapRunner"),
                            "production checks must leave zero bootstrap active paths for " + profile));
        }
    }

    @Test
    void prodProfilePreventsBootstrapEvenWhenLocalIsAlsoActive() {
        context("prod,local", false).run(applicationContext ->
                assertFalse(applicationContext.containsBean("oidcDevBootstrapRunner"),
                        "prod must exclude the bootstrap before any ApplicationRunner can execute"));
    }

    @Test
    void localBootstrapRemainsAvailableWhenProductionChecksAreDisabled() {
        context("local", false).run(applicationContext ->
                assertTrue(applicationContext.containsBean("oidcDevBootstrapRunner")));
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
                        "app.security.oidc-dev-bootstrap.enabled=true",
                        "platform.runtime.production-checks-enabled=" + productionChecks)
                .withBean(OAuth2SecurityProperties.class,
                        () -> new OAuth2SecurityProperties(
                                false, null, null, null, null, null,
                                false, false, false, null))
                .withBean(DevWorkspaceBootstrapService.class,
                        () -> new DevWorkspaceBootstrapService(
                                null, null, null, null, null))
                .withUserConfiguration(OidcDevBootstrapRunner.class);
    }
}
