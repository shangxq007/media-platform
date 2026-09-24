package com.example.platform.execution.composition;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CompositionProviderBoundPlanningDecisionTest {

    @Test
    void blocksWhenCanonicalProviderBoundProjectionIsUnavailable() {
        var decision = CompositionProviderBoundPlanningDecision.blocked(
                CompositionProviderBoundPlanningDecision.Blocker.PROVIDER_BOUND_GRAPH_AUTHORITY_UNAVAILABLE,
                "validated Composition has no authoritative provider-bound graph projection");

        assertFalse(decision.ready());
        assertEquals(CompositionProviderBoundPlanningDecision.Status.BLOCKED,
                decision.status());
        assertEquals(CompositionProviderBoundPlanningDecision.Blocker.PROVIDER_BOUND_GRAPH_AUTHORITY_UNAVAILABLE,
                decision.blocker());
    }

    @Test
    void blockedDecisionRequiresATypedReason() {
        assertThrows(IllegalArgumentException.class, () ->
                new CompositionProviderBoundPlanningDecision(
                        CompositionProviderBoundPlanningDecision.Status.BLOCKED, null,
                        "missing blocker"));
    }
}
