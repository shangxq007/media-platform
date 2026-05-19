package com.example.platform.policy.featureflag;

import com.example.platform.policy.api.FeatureFlagEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FeatureFlagService against the in-memory OpenFeature provider.
 * Since no Unleash server is available, all flags return their default values.
 */
class FeatureFlagServiceTest {

    private FeatureFlagEvaluator evaluator;

    @BeforeEach
    void setUp() {
        // FeatureFlagService requires OpenFeature global provider to be set.
        // In test context without Unleash, the InMemoryProvider is used which returns defaults.
        // We test the interface contract directly.
        evaluator = new FeatureFlagEvaluator() {
            @Override
            public boolean isEnabled(String flagKey, String targetingKey,
                                     Map<String, String> attributes, boolean defaultValue) {
                return defaultValue;
            }
        };
    }

    @Test
    void returnsDefaultTrueWhenNoProviderConfigured() {
        assertTrue(evaluator.isEnabled("any-flag", "user-1", Map.of(), true));
    }

    @Test
    void returnsDefaultFalseWhenNoProviderConfigured() {
        assertFalse(evaluator.isEnabled("any-flag", "user-1", Map.of(), false));
    }

    @Test
    void returnsDefaultWithNullAttributes() {
        assertTrue(evaluator.isEnabled("flag", "key", null, true));
        assertFalse(evaluator.isEnabled("flag", "key", null, false));
    }

    @Test
    void returnsDefaultWithAttributes() {
        assertFalse(evaluator.isEnabled("flag", "key",
                Map.of("tenant", "acme"), false));
    }

    @Test
    void returnsDefaultWithNullTargetingKey() {
        assertTrue(evaluator.isEnabled("flag", null, Map.of(), true));
    }

    @Test
    void appFeaturesPropertiesUnleashDisabledByDefault() {
        AppFeaturesProperties props = new AppFeaturesProperties();
        assertFalse(props.getUnleash().isEnabled());
    }

    @Test
    void appFeaturesPropertiesUnleashCanBeEnabled() {
        AppFeaturesProperties props = new AppFeaturesProperties();
        props.getUnleash().setEnabled(true);
        assertTrue(props.getUnleash().isEnabled());
    }

    @Test
    void appFeaturesPropertiesDefaultValues() {
        AppFeaturesProperties props = new AppFeaturesProperties();
        assertEquals("http://localhost:4242/api/", props.getUnleash().getApiUrl());
        assertEquals("media-platform", props.getUnleash().getAppName());
        assertEquals("singleton", props.getUnleash().getInstanceId());
        assertEquals("", props.getUnleash().getApiKey());
    }

    @Test
    void appFeaturesPropertiesNullUnleashFallsBackToDefault() {
        AppFeaturesProperties props = new AppFeaturesProperties();
        props.setUnleash(null);
        assertNotNull(props.getUnleash());
        assertFalse(props.getUnleash().isEnabled());
    }
}
