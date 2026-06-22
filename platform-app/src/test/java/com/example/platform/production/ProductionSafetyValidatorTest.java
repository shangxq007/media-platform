package com.example.platform.production;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.app.AppCorsProperties;
import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.commerce.infrastructure.CheckoutSessionRepository;
import com.example.platform.commerce.infrastructure.CommerceCartRepository;
import com.example.platform.policy.featureflag.FeatureFlagJdbcStore;
import com.example.platform.security.JwtProperties;
import com.example.platform.shared.runtime.PlatformRuntimeProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyValidatorTest {

    private static final String PROD_JWT_SECRET =
            "production-secret-key-at-least-256-bits-long-for-hmac-signing!!";

    @Test
    void skipsWhenProductionChecksDisabled() {
        MockEnvironment env = new MockEnvironment();
        PlatformRuntimeProperties props = new PlatformRuntimeProperties();
        props.setProductionChecksEnabled(false);
        var validator = new ProductionSafetyValidator(
                env, props, jwtProps(), corsProps(), emptyProvider(), emptyProvider(), emptyProvider(), emptyProvider());
        assertDoesNotThrow(() -> validator.validateProductionReadiness());
    }

    @Test
    void failsWhenDevProfileAndH2Active() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("platform.runtime.production-checks-enabled", "true")
                .withProperty("spring.profiles.active", "prod,dev")
                .withProperty("app.security.enabled", "false")
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("spring.datasource.url", "jdbc:h2:mem:test")
                .withProperty("app.ai.default-provider", "stubChatProvider");
        PlatformRuntimeProperties props = new PlatformRuntimeProperties();
        props.setProductionChecksEnabled(true);
        var validator = new ProductionSafetyValidator(
                env, props, jwtProps(), corsProps(), emptyProvider(), emptyProvider(), emptyProvider(), emptyProvider());
        assertThrows(IllegalStateException.class, () -> validator.validateProductionReadiness());
    }

    @Test
    void passesWithMinimalProdConfig() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("app.security.enabled", "true")
                .withProperty("app.security.oauth2.enabled", "true")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/mp")
                .withProperty("platform.payment.stripe.enabled", "true")
                .withProperty("platform.payment.stripe.webhook-secret", "whsec_test")
                .withProperty("platform.payment.webhook.allow-unsigned", "false")
                .withProperty("app.ai.default-provider", "openAiChatProvider");
        PlatformRuntimeProperties props = new PlatformRuntimeProperties();
        props.setProductionChecksEnabled(true);

        @SuppressWarnings("unchecked")
        ObjectProvider<CheckoutSessionRepository> checkout = mock(ObjectProvider.class);
        when(checkout.getIfAvailable()).thenReturn(mock(CheckoutSessionRepository.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<CommerceCartRepository> carts = mock(ObjectProvider.class);
        when(carts.getIfAvailable()).thenReturn(mock(CommerceCartRepository.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<SubscriptionJdbcRepository> subs = mock(ObjectProvider.class);
        when(subs.getIfAvailable()).thenReturn(mock(SubscriptionJdbcRepository.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<FeatureFlagJdbcStore> flags = mock(ObjectProvider.class);
        when(flags.getIfAvailable()).thenReturn(mock(FeatureFlagJdbcStore.class));

        var validator = new ProductionSafetyValidator(
                env, props, jwtProps(), corsProps(), checkout, carts, subs, flags);
        assertDoesNotThrow(() -> validator.validateProductionReadiness());
    }

    @Test
    void failsWhenJwtUsesInsecureDefault() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("app.security.enabled", "true")
                .withProperty("app.security.oauth2.enabled", "true")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/mp")
                .withProperty("platform.payment.stripe.enabled", "true")
                .withProperty("platform.payment.stripe.webhook-secret", "whsec_test")
                .withProperty("platform.payment.webhook.allow-unsigned", "false")
                .withProperty("app.ai.default-provider", "openAiChatProvider");
        PlatformRuntimeProperties props = new PlatformRuntimeProperties();
        props.setProductionChecksEnabled(true);
        JwtProperties insecure = new JwtProperties("", 3600000);

        @SuppressWarnings("unchecked")
        ObjectProvider<CheckoutSessionRepository> checkout = mock(ObjectProvider.class);
        when(checkout.getIfAvailable()).thenReturn(mock(CheckoutSessionRepository.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<CommerceCartRepository> carts = mock(ObjectProvider.class);
        when(carts.getIfAvailable()).thenReturn(mock(CommerceCartRepository.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<SubscriptionJdbcRepository> subs = mock(ObjectProvider.class);
        when(subs.getIfAvailable()).thenReturn(mock(SubscriptionJdbcRepository.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<FeatureFlagJdbcStore> flags = mock(ObjectProvider.class);
        when(flags.getIfAvailable()).thenReturn(mock(FeatureFlagJdbcStore.class));

        var validator = new ProductionSafetyValidator(
                env, props, insecure, corsProps(), checkout, carts, subs, flags);
        assertThrows(IllegalStateException.class, () -> validator.validateProductionReadiness());
    }

    @Test
    void failsWhenJwtSecretIsDevDefaultInJwtMode() {
        // Simulates: security.enabled=true, oauth2.enabled=false (HMAC JWT mode), dev-default secret
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("app.security.enabled", "true")
                .withProperty("app.security.oauth2.enabled", "false")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/mp")
                .withProperty("platform.payment.stripe.enabled", "true")
                .withProperty("platform.payment.stripe.webhook-secret", "whsec_test")
                .withProperty("platform.payment.webhook.allow-unsigned", "false")
                .withProperty("app.ai.default-provider", "openAiChatProvider");
        PlatformRuntimeProperties props = new PlatformRuntimeProperties();
        props.setProductionChecksEnabled(true);
        // dev-default placeholder — usesInsecureDefault() returns true
        JwtProperties devDefault = new JwtProperties(JwtProperties.INSECURE_DEV_DEFAULT, 3600000);

        @SuppressWarnings("unchecked")
        ObjectProvider<CheckoutSessionRepository> checkout = mock(ObjectProvider.class);
        when(checkout.getIfAvailable()).thenReturn(mock(CheckoutSessionRepository.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<CommerceCartRepository> carts = mock(ObjectProvider.class);
        when(carts.getIfAvailable()).thenReturn(mock(CommerceCartRepository.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<SubscriptionJdbcRepository> subs = mock(ObjectProvider.class);
        when(subs.getIfAvailable()).thenReturn(mock(SubscriptionJdbcRepository.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<FeatureFlagJdbcStore> flags = mock(ObjectProvider.class);
        when(flags.getIfAvailable()).thenReturn(mock(FeatureFlagJdbcStore.class));

        var validator = new ProductionSafetyValidator(
                env, props, devDefault, corsProps(), checkout, carts, subs, flags);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> validator.validateProductionReadiness());
        assertTrue(ex.getMessage().contains("APP_JWT_SECRET"),
                "Error must mention APP_JWT_SECRET");
    }

    private static JwtProperties jwtProps() {
        return new JwtProperties(PROD_JWT_SECRET, 3600000);
    }

    private static AppCorsProperties corsProps() {
        return new AppCorsProperties(List.of("https://app.example.com"), true, null);
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        return p;
    }

}
