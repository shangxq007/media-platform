package com.example.platform.production;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.commerce.infrastructure.CheckoutSessionRepository;
import com.example.platform.commerce.infrastructure.CommerceCartRepository;
import com.example.platform.policy.featureflag.FeatureFlagJdbcStore;
import com.example.platform.shared.runtime.PlatformRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyValidatorTest {

    @Test
    void skipsWhenProductionChecksDisabled() {
        MockEnvironment env = new MockEnvironment();
        PlatformRuntimeProperties props = new PlatformRuntimeProperties();
        props.setProductionChecksEnabled(false);
        var validator = new ProductionSafetyValidator(
                env, props, emptyProvider(), emptyProvider(), emptyProvider(), emptyProvider());
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
                env, props, emptyProvider(), emptyProvider(), emptyProvider(), emptyProvider());
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

        var validator = new ProductionSafetyValidator(env, props, checkout, carts, subs, flags);
        assertDoesNotThrow(() -> validator.validateProductionReadiness());
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        return p;
    }
}
