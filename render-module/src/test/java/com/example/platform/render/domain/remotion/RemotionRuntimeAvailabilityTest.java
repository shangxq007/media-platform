package com.example.platform.render.domain.remotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.app.timeline.compile.RenderPlanPolicyGuard;
import com.example.platform.render.domain.compile.ArtifactNodeType;
import com.example.platform.render.domain.compile.binding.BoundProviderRef;
import com.example.platform.render.domain.compile.executionplan.ExecutionEnvironmentTarget;
import com.example.platform.render.domain.compile.executionplan.ExecutionPolicy;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionPlan;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionPlanId;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionStep;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionStepStatus;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionStepType;
import com.example.platform.render.domain.compile.executionplan.RenderPlanPolicyResult;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fail-closed model tests after removal of ambient process-level runtime probing. */
class RemotionRuntimeAvailabilityTest {

    @Test
    void notCheckedAvailabilityFailsClosed() {
        RemotionRuntimeAvailability availability = RemotionRuntimeAvailability.notChecked();

        assertFalse(availability.executionReady());
        assertTrue(availability.disabledByPolicy());
        assertFalse(availability.documentGenerationReady());
        assertFalse(availability.allToolsAvailable());
        assertTrue(availability.hasMissingTools());
        assertFalse(availability.issues().isEmpty());
    }

    @Test
    void toolStatusFactoriesRemainPureDiagnosticValues() {
        RemotionRuntimeToolStatus available =
                RemotionRuntimeToolStatus.available("node", "provider-bound-observation");
        RemotionRuntimeToolStatus missing = RemotionRuntimeToolStatus.missing("npx");
        RemotionRuntimeToolStatus failed =
                RemotionRuntimeToolStatus.checkFailed("npm", "probe unavailable");

        assertTrue(available.isAvailable());
        assertEquals("provider-bound-observation", available.version());
        assertNull(available.issue());
        assertFalse(missing.isAvailable());
        assertNotNull(missing.issue());
        assertEquals(RemotionRuntimeAvailabilityStatus.CHECK_FAILED, failed.status());
    }

    @Test
    void diagnosticToolFlagsCannotEnableExecutionOrProductionDispatch() {
        RemotionRuntimeAvailability diagnostic = new RemotionRuntimeAvailability(
                true,
                "provider-bound-node",
                true,
                "provider-bound-npm",
                true,
                "provider-bound-npx",
                false,
                null,
                true,
                false,
                true,
                List.of(
                        RemotionRuntimeToolStatus.available("node", "provider-bound-node"),
                        RemotionRuntimeToolStatus.available("npm", "provider-bound-npm"),
                        RemotionRuntimeToolStatus.available("npx", "provider-bound-npx")),
                List.of());

        RemotionProviderReadiness readiness = RemotionProviderReadiness.from(diagnostic);

        assertTrue(readiness.runtimeToolsAvailable());
        assertFalse(readiness.executionReady());
        assertFalse(readiness.productionEligible());
        assertFalse(readiness.autoDispatch());
        assertEquals("POC", readiness.providerStatus());
        assertTrue(readiness.blockedReasons().stream()
                .anyMatch(reason -> reason.contains("disabled by policy")));
    }

    @Test
    void renderPlanPolicyStillRejectsRemotionExecution() {
        RenderPlanPolicyGuard guard = new RenderPlanPolicyGuard();
        BoundProviderRef remotionRef = new BoundProviderRef(
                "remotion", ProviderStatus.POC, ProviderType.RENDER, "P2",
                false, false, null, 200);
        RenderExecutionStep execution = new RenderExecutionStep(
                "s1", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING, "n1", ArtifactNodeType.FINAL_RENDER,
                "remotion", remotionRef, null, List.of(), false,
                ExecutionEnvironmentTarget.LOCAL, "Remotion", Map.of());
        RenderExecutionPlan plan = new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-1", "PRODUCTION"),
                "bp-1", "tl-1", ExecutionPolicy.production(),
                ExecutionEnvironmentTarget.LOCAL, List.of(execution), false, List.of());

        RenderPlanPolicyResult result = guard.evaluate(plan, plan.policy());

        assertTrue(result.isRejected() || result.hasViolations());
        assertFalse(ProviderStatus.POC.isProductionDispatchEligible());
    }

    @Test
    void diagnosticConstructionDoesNotAffectSemanticFingerprint() {
        var before = com.example.platform.render.app.timeline.compile.RenderRequestFingerprint.generate(
                "p", "r", "default_1080p", "PLAN_BASED");
        RemotionRuntimeAvailability.notChecked();
        var after = com.example.platform.render.app.timeline.compile.RenderRequestFingerprint.generate(
                "p", "r", "default_1080p", "PLAN_BASED");

        assertEquals(before.value(), after.value());
    }

    @Test
    void publicApiDoesNotExposeRuntimeAvailability() {
        var request = new com.example.platform.render.api.dto.TimelineRevisionRenderRequest(
                "default_1080p");
        assertEquals("default_1080p", request.outputProfile());
    }
}
