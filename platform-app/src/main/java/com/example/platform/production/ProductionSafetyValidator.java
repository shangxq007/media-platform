package com.example.platform.production;

import com.example.platform.app.AppCorsProperties;
import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.commerce.infrastructure.CheckoutSessionRepository;
import com.example.platform.commerce.infrastructure.CommerceCartRepository;
import com.example.platform.policy.featureflag.FeatureFlagJdbcStore;
import com.example.platform.security.JwtProperties;
import com.example.platform.shared.runtime.PlatformRuntimeProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class ProductionSafetyValidator {

    private final Environment environment;
    private final PlatformRuntimeProperties runtimeProperties;
    private final JwtProperties jwtProperties;
    private final AppCorsProperties corsProperties;
    private final org.springframework.beans.factory.ObjectProvider<CheckoutSessionRepository> checkoutSessions;
    private final org.springframework.beans.factory.ObjectProvider<CommerceCartRepository> commerceCarts;
    private final org.springframework.beans.factory.ObjectProvider<SubscriptionJdbcRepository> subscriptionStore;
    private final org.springframework.beans.factory.ObjectProvider<FeatureFlagJdbcStore> featureFlagStore;

    public ProductionSafetyValidator(
            Environment environment,
            PlatformRuntimeProperties runtimeProperties,
            JwtProperties jwtProperties,
            AppCorsProperties corsProperties,
            @Qualifier("checkoutSessionRepository") org.springframework.beans.factory.ObjectProvider<CheckoutSessionRepository> checkoutSessions,
            @Qualifier("commerceCartRepository") org.springframework.beans.factory.ObjectProvider<CommerceCartRepository> commerceCarts,
            @Qualifier("subscriptionJdbcRepository") org.springframework.beans.factory.ObjectProvider<SubscriptionJdbcRepository> subscriptionStore,
            @Qualifier("featureFlagJdbcStore") org.springframework.beans.factory.ObjectProvider<FeatureFlagJdbcStore> featureFlagStore) {
        this.environment = environment;
        this.runtimeProperties = runtimeProperties;
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.checkoutSessions = checkoutSessions;
        this.commerceCarts = commerceCarts;
        this.subscriptionStore = subscriptionStore;
        this.featureFlagStore = featureFlagStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateProductionReadiness() {
        if (!runtimeProperties.isProductionChecksEnabled()) {
            return;
        }
        List<String> errors = new ArrayList<>();

        if (hasProfile("dev")) {
            errors.add("spring profile 'dev' must not be active in production");
        }
        if (!getBool("app.security.enabled", false)) {
            errors.add("app.security.enabled must be true");
        }
        if (!getBool("app.security.oauth2.enabled", false)
                && !getBool("app.security.dev-auth-endpoint", false)
                && !getBool("app.security.oauth2.legacy-hmac-jwt-enabled", false)) {
            errors.add(
                    "configure OIDC (app.security.oauth2.enabled=true) or an approved legacy JWT path for production");
        }
        if (jwtProperties.usesInsecureDefault()) {
            errors.add("APP_JWT_SECRET must be set to a strong secret (not blank or dev default)");
        }
        if (corsProperties.hasWildcardOriginWithCredentials()) {
            errors.add("app.security.cors must not use wildcard origins when allow-credentials is true");
        }
        if (getBool("platform.payment.webhook.allow-unsigned", false)) {
            errors.add("platform.payment.webhook.allow-unsigned must be false in production");
        }
        if (!getBool("spring.flyway.enabled", false)) {
            errors.add("spring.flyway.enabled must be true");
        }

        String dsUrl = environment.getProperty("spring.datasource.url", "");
        if (dsUrl.isBlank() || dsUrl.toLowerCase().contains(":h2:")) {
            errors.add("spring.datasource.url must be PostgreSQL (H2 is dev-only)");
        }

        boolean stripe = getBool("platform.payment.stripe.enabled", false);
        boolean hyperswitch = getBool("platform.payment.hyperswitch.enabled", false);
        if (!stripe && !hyperswitch) {
            errors.add("enable platform.payment.stripe.enabled or platform.payment.hyperswitch.enabled");
        }
        if (stripe) {
            String webhookSecret = environment.getProperty("platform.payment.stripe.webhook-secret", "");
            if (webhookSecret == null || webhookSecret.isBlank()) {
                errors.add("platform.payment.stripe.webhook-secret required when Stripe is enabled");
            }
        }
        if (hyperswitch) {
            String hsWebhookSecret = environment.getProperty("platform.payment.hyperswitch.webhook-secret", "");
            if (hsWebhookSecret == null || hsWebhookSecret.isBlank()) {
                errors.add("platform.payment.hyperswitch.webhook-secret required when Hyperswitch is enabled");
            }
        }

        String aiProvider = environment.getProperty("app.ai.default-provider", "stubChatProvider");
        if (aiProvider == null || aiProvider.isBlank() || aiProvider.contains("stub")) {
            errors.add("app.ai.default-provider must not be a stub provider in production");
        }

        if (featureFlagStore.getIfAvailable() == null) {
            errors.add("FeatureFlagJdbcStore bean required (feature flags must persist to database)");
        }

        if (checkoutSessions.getIfAvailable() == null) {
            errors.add("CheckoutSessionRepository required (commerce checkout must use JDBC, not memory-only)");
        }
        if (commerceCarts.getIfAvailable() == null) {
            errors.add("CommerceCartRepository required (commerce carts must persist to database)");
        }
        if (subscriptionStore.getIfAvailable() == null) {
            errors.add("SubscriptionJdbcRepository required (billing subscriptions must use JDBC authority)");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Production safety validation failed:\n- " + String.join("\n- ", errors));
        }
    }

    private boolean hasProfile(String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }

    private boolean getBool(String key, boolean defaultValue) {
        return environment.getProperty(key, Boolean.class, defaultValue);
    }
}
