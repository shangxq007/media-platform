package com.example.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

/**
 * AUTH-UNPROTECTED-FIX-002 (1.1) — the dev auth controller must only be registered when the
 * property is on AND the active profile is not production-like. This is the behavioural proof that
 * removing either the property condition or the profile condition changes the outcome.
 */
class DevAuthControllerConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            // withBean keeps the collaborators out of component scanning (a nested @Configuration
            // in this package is picked up by the application's own scan).
            .withBean(JwtProperties.class, () -> new JwtProperties(
                    "test-secret-key-that-is-at-least-256-bits-long-for-hmac!", 3_600_000))
            .withBean(DevAuthSecretGuard.class,
                    () -> new DevAuthSecretGuard(new MockEnvironment(), true, "test-secret"))
            .withUserConfiguration(DevAuthController.class);

    @Test
    void registeredWhenPropertyOnAndDevProfile() {
        runner.withPropertyValues("app.security.dev-auth-endpoint=true")
                .run(context -> assertThat(context).hasSingleBean(DevAuthController.class));
    }

    @Test
    void absentWhenPropertyMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DevAuthController.class));
    }

    @Test
    void absentWhenPropertyOff() {
        runner.withPropertyValues("app.security.dev-auth-endpoint=false",
                        "app.security.dev-auth-secret=whatever")
                .run(context -> assertThat(context).doesNotHaveBean(DevAuthController.class));
    }

    @Test
    void absentUnderEveryProductionLikeProfile() {
        for (String profile : new String[] {"prod", "safe-mode", "oidc"}) {
            runner.withPropertyValues("app.security.dev-auth-endpoint=true",
                            "spring.profiles.active=" + profile)
                    .run(context -> assertThat(context)
                            .as("dev auth controller must not exist under profile " + profile)
                            .doesNotHaveBean(DevAuthController.class));
        }
    }
}
