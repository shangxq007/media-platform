package com.example.platform.providerplugin.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderVisualCapabilityContractTest {

    @Test
    void providerSupportKeepsEligibilityAndFallbackInRuntime() {
        ProviderVisualCapabilitySupport support = support(
                "provider-a", "CAPTION_OVERLAY", ProviderVisualCapabilityStatus.PRODUCTION,
                ProviderVisualConsistencyLevel.EXACT,
                ProviderVisualFallbackBehavior.NO_FALLBACK, true, true);

        assertTrue(support.isProductionEligible());
        assertTrue(support.isAutoDispatchEligible());
        assertEquals("provider-a", support.providerId());
        assertEquals(ProviderVisualFallbackBehavior.NO_FALLBACK, support.fallbackBehavior());
    }

    @Test
    void forbiddenOrPocProfilesCannotBecomeDispatchEligible() {
        ProviderVisualCapabilitySupport forbidden = support(
                "remotion", "REMOTION_COMPONENT_EXECUTION", ProviderVisualCapabilityStatus.FORBIDDEN,
                ProviderVisualConsistencyLevel.FORBIDDEN,
                ProviderVisualFallbackBehavior.REJECT_REQUEST, true, true);
        ProviderVisualCapabilitySupport poc = support(
                "natron", "NATRON_NODE_GRAPH", ProviderVisualCapabilityStatus.POC,
                ProviderVisualConsistencyLevel.PROVIDER_SPECIFIC,
                ProviderVisualFallbackBehavior.REJECT_REQUEST, true, true);

        assertFalse(forbidden.isProductionEligible());
        assertFalse(forbidden.isAutoDispatchEligible());
        assertFalse(poc.isProductionEligible());
        assertFalse(poc.isAutoDispatchEligible());
    }

    @Test
    void matrixUsesOpaqueRenderCapabilityKeysWithoutRenderDependency() {
        ProviderVisualCapabilitySupport allowed = support(
                "provider-a", "SCALE", ProviderVisualCapabilityStatus.PRODUCTION,
                ProviderVisualConsistencyLevel.EXACT,
                ProviderVisualFallbackBehavior.NO_FALLBACK, true, true);
        ProviderVisualCapabilitySupport forbidden = support(
                "provider-b", "ARBITRARY_PROVIDER_EXPRESSION", ProviderVisualCapabilityStatus.FORBIDDEN,
                ProviderVisualConsistencyLevel.FORBIDDEN,
                ProviderVisualFallbackBehavior.REJECT_REQUEST, false, false);
        ProviderVisualCapabilityMatrix matrix = new ProviderVisualCapabilityMatrix(
                List.of(allowed, forbidden), Map.of("scope", "runtime"));

        assertTrue(matrix.findSupport("provider-a", "SCALE").isPresent());
        assertFalse(matrix.findSupport("provider-a", "BLUR").isPresent());
        assertTrue(matrix.hasForbiddenCapabilities());
        assertEquals(List.of(allowed), matrix.findProductionEligible());
    }

    private static ProviderVisualCapabilitySupport support(
            String providerId,
            String capabilityId,
            ProviderVisualCapabilityStatus status,
            ProviderVisualConsistencyLevel consistency,
            ProviderVisualFallbackBehavior fallback,
            boolean autoDispatchAllowed,
            boolean productionAllowed) {
        return new ProviderVisualCapabilitySupport(
                providerId, capabilityId, ProviderVisualCapabilityCategory.EFFECT, status,
                consistency, fallback, autoDispatchAllowed, productionAllowed, Map.of());
    }
}
