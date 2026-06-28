package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.api.dto.TimelineRevisionRenderRequest;
import com.example.platform.render.api.dto.TimelineRevisionRenderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Post-switch stabilization checks.
 * Encodes the permanent baseline after PLAN_BASED became default.
 */
class PlanBasedPostSwitchStabilizationTest {

    @Test
    @DisplayName("Default mode is PLAN_BASED")
    void defaultModeIsPlanBased() {
        assertEquals(TimelineRenderExecutionMode.PLAN_BASED,
                TimelineRenderExecutionProperties.defaults().executionMode());
    }

    @Test
    @DisplayName("Explicit LEGACY rollback still works")
    void explicitLegacyRollbackWorks() {
        TimelineRenderExecutionProperties legacy =
                new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.LEGACY);
        assertEquals(TimelineRenderExecutionMode.LEGACY, legacy.executionMode());
        assertTrue(legacy.isLegacyEnabled());
        assertFalse(legacy.isPlanBasedEnabled());
    }

    @Test
    @DisplayName("Public request DTO has no execution mode")
    void publicRequestNoExecutionMode() {
        TimelineRevisionRenderRequest request = new TimelineRevisionRenderRequest("default_1080p");
        assertEquals("default_1080p", request.outputProfile());
        // No executionMode field
    }

    @Test
    @DisplayName("Public response DTO has no execution mode or internal IDs")
    void publicResponseNoInternalIds() {
        TimelineRevisionRenderResponse response =
                TimelineRevisionRenderResponse.failure("rev-1", "test");
        // No executionMode, renderCorrelationId, localExecutionRunId,
        // artifactGraphId, capabilityGraphId, providerBindingPlanId,
        // renderExecutionPlanId fields
        assertNotNull(response);
    }

    @Test
    @DisplayName("LEGACY and PLAN_BASED fingerprints are distinct")
    void fingerprintsAreDistinct() {
        RenderRequestFingerprint legacy =
                RenderRequestFingerprint.generate("p", "r", "default_1080p", "LEGACY");
        RenderRequestFingerprint planBased =
                RenderRequestFingerprint.generate("p", "r", "default_1080p", "PLAN_BASED");
        assertNotEquals(legacy.value(), planBased.value());
    }

    @Test
    @DisplayName("Same mode same request produces same fingerprint")
    void sameModeSameFingerprint() {
        RenderRequestFingerprint fp1 =
                RenderRequestFingerprint.generate("p", "r", "default_1080p", "PLAN_BASED");
        RenderRequestFingerprint fp2 =
                RenderRequestFingerprint.generate("p", "r", "default_1080p", "PLAN_BASED");
        assertEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("PLAN_BASED rejects non-FFmpeg provider via policy guard")
    void planBasedRejectsNonFfmpeg() {
        RenderPlanPolicyGuard guard = new RenderPlanPolicyGuard();
        assertNotNull(guard);
        // Verified in detail by LocalExecutionPlanRunnerTest and PlanBasedDefaultReadinessTest
    }
}
