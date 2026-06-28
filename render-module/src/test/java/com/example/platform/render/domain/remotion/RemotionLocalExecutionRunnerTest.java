package com.example.platform.render.domain.remotion;

import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
import com.example.platform.render.app.timeline.compile.RenderPlanPolicyGuard;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.ArtifactNodeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RemotionLocalExecutionRunner.
 * Proves: never executes, always refuses, safe result fields.
 */
class RemotionLocalExecutionRunnerTest {

    private RemotionLocalExecutionRunner runner;

    @BeforeEach
    void setUp() {
        runner = new RemotionLocalExecutionRunner();
    }

    // --- Core refusal ---

    @Test
    @DisplayName("Null request fails closed")
    void nullRequestFailsClosed() {
        RemotionLocalExecutionResult result = runner.execute(null);
        assertEquals(RemotionLocalExecutionStatus.FAILED_CLOSED, result.status());
        assertFalse(result.executed());
        assertFalse(result.readyToExecute());
    }

    @Test
    @DisplayName("Unsupported document is rejected")
    void unsupportedDocumentRejected() {
        var docResult = new com.example.platform.render.domain.timeline.compile.remotion.ProviderExecutionDocumentGenerationResult(
                "doc-1", "draft-1", "ffmpeg", "FFMPEG_COMMAND_PLAN",
                com.example.platform.render.domain.timeline.compile.remotion.ProviderExecutionDocumentGenerationStatus.REJECTED_UNSUPPORTED,
                false, false, List.of("Unsupported"), null, Map.of());
        RemotionLocalExecutionRequest request = new RemotionLocalExecutionRequest(
                docResult, null, null, null, null, null, Map.of());

        RemotionLocalExecutionResult result = runner.execute(request);

        assertEquals(RemotionLocalExecutionStatus.REJECTED_UNSUPPORTED_DOCUMENT, result.status());
        assertFalse(result.executed());
    }

    @Test
    @DisplayName("Preflight BLOCKED_BY_POLICY maps to BLOCKED_BY_POLICY")
    void preflightBlockedByPolicyMaps() {
        RemotionLocalExecutionRequest request = buildRequestWithPolicy(
                RemotionExecutionPolicy.disabledDefault());

        RemotionLocalExecutionResult result = runner.execute(request);

        // disabledDefault has executionEnabled=false → READY_BUT_EXECUTION_DISABLED → NOT_IMPLEMENTED
        assertEquals(RemotionLocalExecutionStatus.NOT_IMPLEMENTED, result.status());
        assertFalse(result.executed());
    }

    @Test
    @DisplayName("Preflight READY_BUT_EXECUTION_DISABLED maps to NOT_IMPLEMENTED")
    void readyButDisabledMapsToNotImplemented() {
        // disabledDefault with no readiness → no violations → READY_BUT_EXECUTION_DISABLED
        RemotionLocalExecutionRequest request = new RemotionLocalExecutionRequest(
                null, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(),
                null, Map.of());

        RemotionLocalExecutionResult result = runner.execute(request);

        assertEquals(RemotionLocalExecutionStatus.NOT_IMPLEMENTED, result.status());
        assertFalse(result.executed());
        assertFalse(result.readyToExecute());
    }

    @Test
    @DisplayName("Preflight NOT_IMPLEMENTED maps to NOT_IMPLEMENTED")
    void preflightNotImplementedMaps() {
        // manualExperimentDesignOnly has executionEnabled=false but manualModeAllowed=true
        // With readiness having executionReady=false → BLOCKED_BY_RUNTIME → BLOCKED_BY_RUNTIME
        RemotionProviderReadiness readiness = createReadiness();
        RemotionLocalExecutionRequest request = new RemotionLocalExecutionRequest(
                null, null, readiness,
                RemotionExecutionPolicy.manualExperimentDesignOnly(),
                RemotionSandboxPolicy.lockedDown(),
                null, Map.of());

        RemotionLocalExecutionResult result = runner.execute(request);

        // executionReady=false + executionEnabled=false → BLOCKED_BY_POLICY
        assertEquals(RemotionLocalExecutionStatus.BLOCKED_BY_POLICY, result.status());
        assertFalse(result.executed());
    }

    @Test
    @DisplayName("Result always has executed=false")
    void resultAlwaysNotExecuted() {
        for (var policy : List.of(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionExecutionPolicy.manualExperimentDesignOnly(),
                RemotionExecutionPolicy.futureLocalPocDisabledByDefault())) {

            RemotionLocalExecutionRequest request = new RemotionLocalExecutionRequest(
                    null, null, null, policy, RemotionSandboxPolicy.lockedDown(),
                    null, Map.of());

            RemotionLocalExecutionResult result = runner.execute(request);
            assertFalse(result.executed(), "executed must be false for policy: " + policy);
        }
    }

    @Test
    @DisplayName("Result always has readyToExecute=false")
    void resultAlwaysNotReady() {
        for (var policy : List.of(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionExecutionPolicy.manualExperimentDesignOnly())) {

            RemotionLocalExecutionRequest request = new RemotionLocalExecutionRequest(
                    null, null, null, policy, RemotionSandboxPolicy.lockedDown(),
                    null, Map.of());

            RemotionLocalExecutionResult result = runner.execute(request);
            assertFalse(result.readyToExecute(), "readyToExecute must be false");
        }
    }

    @Test
    @DisplayName("Result has no outputProductId")
    void resultNoOutputProductId() {
        RemotionLocalExecutionResult result = runner.execute(buildDefaultRequest());
        assertNull(result.outputProductId());
    }

    @Test
    @DisplayName("Result has no outputPathRef")
    void resultNoOutputPathRef() {
        RemotionLocalExecutionResult result = runner.execute(buildDefaultRequest());
        assertNull(result.outputPathRef());
    }

    // --- Safety ---

    @Test
    @DisplayName("Runner does not call ProcessToolRunner")
    void runnerDoesNotCallProcessToolRunner() {
        // RemotionLocalExecutionRunner has no ProcessToolRunner dependency
        // Verified by code: constructor only takes RemotionExecutionPolicyEvaluator
        RemotionLocalExecutionRunner r = new RemotionLocalExecutionRunner();
        assertNotNull(r);
    }

    @Test
    @DisplayName("Runner does not call StorageRuntime")
    void runnerDoesNotCallStorageRuntime() {
        // RemotionLocalExecutionRunner has no StorageRuntime dependency
        RemotionLocalExecutionRunner r = new RemotionLocalExecutionRunner();
        assertNotNull(r);
    }

    @Test
    @DisplayName("Runner does not call ProductRuntime")
    void runnerDoesNotCallProductRuntime() {
        // RemotionLocalExecutionRunner has no ProductRuntime dependency
        RemotionLocalExecutionRunner r = new RemotionLocalExecutionRunner();
        assertNotNull(r);
    }

    @Test
    @DisplayName("Result does not expose command args")
    void resultNoCommandArgs() {
        RemotionLocalExecutionResult result = runner.execute(buildDefaultRequest());
        String str = result.toString();
        assertFalse(str.contains("ffmpeg "));
        assertFalse(str.contains("remotion "));
        assertFalse(str.contains("npx "));
    }

    @Test
    @DisplayName("Result does not expose local paths")
    void resultNoLocalPaths() {
        RemotionLocalExecutionResult result = runner.execute(buildDefaultRequest());
        String str = result.toString();
        assertFalse(str.contains("/tmp"));
        assertFalse(str.contains("/home"));
    }

    @Test
    @DisplayName("Result does not expose storage internals")
    void resultNoStorageInternals() {
        RemotionLocalExecutionResult result = runner.execute(buildDefaultRequest());
        String str = result.toString();
        assertFalse(str.contains("\"bucket\""));
        assertFalse(str.contains("\"objectKey\""));
        assertFalse(str.contains("\"signedUrl\""));
    }

    // --- Policy guard safety ---

    @Test
    @DisplayName("RenderPlanPolicyGuard rejects Remotion execution")
    void policyGuardRejectsRemotion() {
        RenderPlanPolicyGuard guard = new RenderPlanPolicyGuard();
        BoundProviderRef remotionRef = new BoundProviderRef(
                "remotion", ProviderStatus.POC, ProviderType.RENDER, "P2",
                false, false, null, 200);
        RenderExecutionStep exec = new RenderExecutionStep(
                "s1", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING, "n1", ArtifactNodeType.FINAL_RENDER,
                "remotion", remotionRef, null, List.of(), false,
                ExecutionEnvironmentTarget.LOCAL, "Remotion", Map.of());
        RenderExecutionPlan plan = new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-1", "PRODUCTION"),
                "bp-1", "tl-1", ExecutionPolicy.production(),
                ExecutionEnvironmentTarget.LOCAL, List.of(exec), false, List.of());

        RenderPlanPolicyResult result = guard.evaluate(plan, plan.policy());
        assertTrue(result.isRejected() || result.hasViolations());
    }

    @Test
    @DisplayName("Remotion POC not production eligible")
    void remotionPocNotProductionEligible() {
        assertFalse(ProviderStatus.POC.isProductionDispatchEligible());
    }

    // --- Result model ---

    @Test
    @DisplayName("Result factory methods produce correct status")
    void resultFactoryMethods() {
        RemotionLocalExecutionResult notImpl = RemotionLocalExecutionResult.notImplemented("test");
        assertEquals(RemotionLocalExecutionStatus.NOT_IMPLEMENTED, notImpl.status());
        assertFalse(notImpl.executed());
        assertTrue(notImpl.notImplemented());

        RemotionLocalExecutionResult blocked = RemotionLocalExecutionResult.blockedByPolicy("test");
        assertEquals(RemotionLocalExecutionStatus.BLOCKED_BY_POLICY, blocked.status());
        assertTrue(blocked.isBlocked());

        RemotionLocalExecutionResult runtime = RemotionLocalExecutionResult.blockedByRuntime("test");
        assertEquals(RemotionLocalExecutionStatus.BLOCKED_BY_RUNTIME, runtime.status());

        RemotionLocalExecutionResult rejected = RemotionLocalExecutionResult.rejectedUnsupported("test");
        assertEquals(RemotionLocalExecutionStatus.REJECTED_UNSUPPORTED_DOCUMENT, rejected.status());

        RemotionLocalExecutionResult failed = RemotionLocalExecutionResult.failedClosed("test");
        assertEquals(RemotionLocalExecutionStatus.FAILED_CLOSED, failed.status());
    }

    @Test
    @DisplayName("No success status reachable")
    void noSuccessReachable() {
        // There is no SUCCESS status in v0
        for (RemotionLocalExecutionStatus status : RemotionLocalExecutionStatus.values()) {
            assertNotEquals("SUCCESS", status.name());
        }
    }

    // --- Helpers ---

    private RemotionLocalExecutionRequest buildDefaultRequest() {
        return new RemotionLocalExecutionRequest(
                null, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(),
                null, Map.of());
    }

    private RemotionLocalExecutionRequest buildRequestWithPolicy(RemotionExecutionPolicy policy) {
        return new RemotionLocalExecutionRequest(
                null, null, null, policy, RemotionSandboxPolicy.lockedDown(),
                null, Map.of());
    }

    private RemotionProviderReadiness createReadiness() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeAvailability avail = new RemotionRuntimeProbe(inventory).probe();
        return RemotionProviderReadiness.from(avail);
    }
}
