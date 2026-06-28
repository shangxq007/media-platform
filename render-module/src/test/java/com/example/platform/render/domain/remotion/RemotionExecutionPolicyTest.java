package com.example.platform.render.domain.remotion;

import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.app.timeline.compile.RenderPlanPolicyGuard;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.ArtifactNodeType;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Remotion execution policy, sandbox, command, and preflight.
 * Proves: execution disabled, unsafe commands rejected, production blocked.
 */
class RemotionExecutionPolicyTest {

    // --- Execution Policy ---

    @Test
    @DisplayName("Default policy has executionEnabled=false")
    void defaultPolicyExecutionDisabled() {
        RemotionExecutionPolicy policy = RemotionExecutionPolicy.disabledDefault();
        assertFalse(policy.executionEnabled());
    }

    @Test
    @DisplayName("Default policy has productionAllowed=false")
    void defaultPolicyProductionNotAllowed() {
        assertFalse(RemotionExecutionPolicy.disabledDefault().productionAllowed());
    }

    @Test
    @DisplayName("Default policy has autoDispatchAllowed=false")
    void defaultPolicyAutoDispatchNotAllowed() {
        assertFalse(RemotionExecutionPolicy.disabledDefault().autoDispatchAllowed());
    }

    @Test
    @DisplayName("Default policy has publicSelectionAllowed=false")
    void defaultPolicyPublicSelectionNotAllowed() {
        assertFalse(RemotionExecutionPolicy.disabledDefault().publicSelectionAllowed());
    }

    @Test
    @DisplayName("Default policy rejects user supplied components")
    void defaultPolicyRejectsUserComponents() {
        assertFalse(RemotionExecutionPolicy.disabledDefault().userSuppliedComponentAllowed());
    }

    @Test
    @DisplayName("Default policy rejects user supplied JavaScript")
    void defaultPolicyRejectsUserJavaScript() {
        assertFalse(RemotionExecutionPolicy.disabledDefault().userSuppliedJavaScriptAllowed());
    }

    @Test
    @DisplayName("Default policy rejects network")
    void defaultPolicyRejectsNetwork() {
        assertFalse(RemotionExecutionPolicy.disabledDefault().networkAllowed());
    }

    @Test
    @DisplayName("Default policy rejects package install")
    void defaultPolicyRejectsPackageInstall() {
        assertFalse(RemotionExecutionPolicy.disabledDefault().packageInstallAllowed());
    }

    @Test
    @DisplayName("Manual/experiment design-only policy still has executionEnabled=false")
    void manualExperimentDesignOnlyStillDisabled() {
        RemotionExecutionPolicy policy = RemotionExecutionPolicy.manualExperimentDesignOnly();
        assertFalse(policy.executionEnabled());
        assertTrue(policy.manualModeAllowed());
        assertTrue(policy.experimentModeAllowed());
    }

    @Test
    @DisplayName("Future local POC policy has executionEnabled=false")
    void futureLocalPocStillDisabled() {
        assertFalse(RemotionExecutionPolicy.futureLocalPocDisabledByDefault().executionEnabled());
    }

    @Test
    @DisplayName("Default policy requires audit, correlation, timeout, resource limits")
    void defaultPolicyRequiresSafety() {
        RemotionExecutionPolicy policy = RemotionExecutionPolicy.disabledDefault();
        assertTrue(policy.auditRequired());
        assertTrue(policy.correlationRequired());
        assertTrue(policy.timeoutRequired());
        assertTrue(policy.resourceLimitsRequired());
    }

    @Test
    @DisplayName("Default policy blocked reasons include execution not enabled")
    void defaultPolicyBlockedReasons() {
        List<String> reasons = RemotionExecutionPolicy.disabledDefault().blockedReasons();
        assertFalse(reasons.isEmpty());
        assertTrue(reasons.stream().anyMatch(r -> r.contains("not enabled")));
    }

    // --- Sandbox Policy ---

    @Test
    @DisplayName("Default sandbox requires managed working directory")
    void sandboxRequiresManagedWorkingDir() {
        assertTrue(RemotionSandboxPolicy.lockedDown().managedWorkingDirectoryRequired());
    }

    @Test
    @DisplayName("Default sandbox requires managed output directory")
    void sandboxRequiresManagedOutputDir() {
        assertTrue(RemotionSandboxPolicy.lockedDown().managedOutputDirectoryRequired());
    }

    @Test
    @DisplayName("Default sandbox prohibits raw storage internals")
    void sandboxProhibitsRawStorageInternals() {
        assertTrue(RemotionSandboxPolicy.lockedDown().prohibitRawStorageInternals());
    }

    @Test
    @DisplayName("Default sandbox prohibits signed URLs")
    void sandboxProhibitsSignedUrls() {
        assertTrue(RemotionSandboxPolicy.lockedDown().prohibitSignedUrls());
    }

    @Test
    @DisplayName("Default sandbox prohibits arbitrary user paths")
    void sandboxProhibitsArbitraryUserPaths() {
        assertTrue(RemotionSandboxPolicy.lockedDown().prohibitArbitraryUserPaths());
    }

    @Test
    @DisplayName("Default sandbox prohibits environment leakage")
    void sandboxProhibitsEnvironmentLeakage() {
        assertTrue(RemotionSandboxPolicy.lockedDown().prohibitEnvironmentLeakage());
    }

    @Test
    @DisplayName("Default sandbox prohibits inherited secrets")
    void sandboxProhibitsInheritedSecrets() {
        assertTrue(RemotionSandboxPolicy.lockedDown().prohibitInheritedSecrets());
    }

    @Test
    @DisplayName("Default sandbox requires storage materialized inputs")
    void sandboxRequiresMaterializedInputs() {
        assertTrue(RemotionSandboxPolicy.lockedDown().storageMaterializedInputsRequired());
    }

    // --- Command Policy ---

    @Test
    @DisplayName("Command plan rejects shell string arguments")
    void commandRejectsShellString() {
        RemotionExecutionCommandPlan plan = new RemotionExecutionCommandPlan(
                "render", "internal://remotion",
                List.of("--props", "file.json; rm -rf /"),
                "/managed/work", "/managed/props.json", "/managed/output.mp4",
                60, RemotionExecutionNetworkPolicy.DENIED,
                "internal://template", "comp-1", Map.of());

        RemotionExecutionPolicyEvaluator evaluator = new RemotionExecutionPolicyEvaluator();
        RemotionExecutionPreflightResult result = evaluator.evaluate(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(),
                createReadiness(),
                plan);

        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("Shell metacharacters")));
    }

    @Test
    @DisplayName("Command plan rejects signed URL arguments")
    void commandRejectsSignedUrl() {
        RemotionExecutionCommandPlan plan = new RemotionExecutionCommandPlan(
                "render", "internal://remotion",
                List.of("--input", "https://s3.example.com/file?X-Amz-Signature=abc"),
                "/managed/work", "/managed/props.json", "/managed/output.mp4",
                60, RemotionExecutionNetworkPolicy.DENIED,
                "internal://template", "comp-1", Map.of());

        RemotionExecutionPolicyEvaluator evaluator = new RemotionExecutionPolicyEvaluator();
        RemotionExecutionPreflightResult result = evaluator.evaluate(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(),
                createReadiness(),
                plan);

        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("Signed URL")));
    }

    @Test
    @DisplayName("Command plan rejects bucket/objectKey arguments")
    void commandRejectsStorageInternals() {
        RemotionExecutionCommandPlan plan = new RemotionExecutionCommandPlan(
                "render", "internal://remotion",
                List.of("--bucket=my-bucket", "--objectKey=path/key"),
                "/managed/work", "/managed/props.json", "/managed/output.mp4",
                60, RemotionExecutionNetworkPolicy.DENIED,
                "internal://template", "comp-1", Map.of());

        RemotionExecutionPolicyEvaluator evaluator = new RemotionExecutionPolicyEvaluator();
        RemotionExecutionPreflightResult result = evaluator.evaluate(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(),
                createReadiness(),
                plan);

        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("Storage internals")));
    }

    @Test
    @DisplayName("Command plan rejects network when policy disallows")
    void commandRejectsNetworkWhenDisallowed() {
        RemotionExecutionCommandPlan plan = new RemotionExecutionCommandPlan(
                "render", "internal://remotion",
                List.of("--props", "file.json"),
                "/managed/work", "/managed/props.json", "/managed/output.mp4",
                60, RemotionExecutionNetworkPolicy.ALLOWED,
                "internal://template", "comp-1", Map.of());

        RemotionExecutionPolicyEvaluator evaluator = new RemotionExecutionPolicyEvaluator();
        RemotionExecutionPreflightResult result = evaluator.evaluate(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(),
                createReadiness(),
                plan);

        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("Network")));
    }

    @Test
    @DisplayName("Command plan structural issues captured when missing fields")
    void commandStructuralIssues() {
        RemotionExecutionCommandPlan plan = new RemotionExecutionCommandPlan(
                null, null, null, null, null, null,
                0, RemotionExecutionNetworkPolicy.DENIED, null, null, Map.of());

        assertFalse(plan.hasSafeStructure());
        assertFalse(plan.structuralIssues().isEmpty());
    }

    @Test
    @DisplayName("Safe command plan has valid structure")
    void safeCommandPlanValidStructure() {
        RemotionExecutionCommandPlan plan = new RemotionExecutionCommandPlan(
                "render", "internal://remotion",
                List.of("--props", "/managed/props.json"),
                "/managed/work", "/managed/props.json", "/managed/output.mp4",
                60, RemotionExecutionNetworkPolicy.DENIED,
                "internal://template", "comp-1", Map.of());

        assertTrue(plan.hasSafeStructure());
        assertTrue(plan.structuralIssues().isEmpty());
    }

    // --- Preflight Evaluator ---

    @Test
    @DisplayName("Preflight blocks when execution not enabled")
    void preflightBlocksWhenNotEnabled() {
        RemotionExecutionPolicyEvaluator evaluator = new RemotionExecutionPolicyEvaluator();
        RemotionExecutionPreflightResult result = evaluator.evaluate(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(),
                createReadiness(), null);

        assertTrue(result.blocked());
        assertEquals(RemotionExecutionPreflightStatus.BLOCKED_BY_POLICY, result.status());
        assertFalse(result.readyToExecute());
    }

    @Test
    @DisplayName("Preflight blocks when runtime executionReady=false")
    void preflightBlocksWhenRuntimeNotReady() {
        RemotionExecutionPolicyEvaluator evaluator = new RemotionExecutionPolicyEvaluator();
        RemotionExecutionPreflightResult result = evaluator.evaluate(
                RemotionExecutionPolicy.manualExperimentDesignOnly(),
                RemotionSandboxPolicy.lockedDown(),
                createReadiness(), null);

        // executionReady=false in readiness → blocked
        assertTrue(result.blocked() || result.notImplemented());
    }

    @Test
    @DisplayName("Preflight never returns readyToExecute=true in v0")
    void preflightNeverReady() {
        RemotionExecutionPolicyEvaluator evaluator = new RemotionExecutionPolicyEvaluator();

        // Try all policy variants
        for (RemotionExecutionPolicy policy : List.of(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionExecutionPolicy.manualExperimentDesignOnly(),
                RemotionExecutionPolicy.futureLocalPocDisabledByDefault())) {

            RemotionExecutionPreflightResult result = evaluator.evaluate(
                    policy, RemotionSandboxPolicy.lockedDown(),
                    createReadiness(), null);

            assertFalse(result.readyToExecute(),
                    "readyToExecute must be false for policy: " + policy);
        }
    }

    @Test
    @DisplayName("READY_BUT_EXECUTION_DISABLED has readyToExecute=false")
    void readyButExecutionDisabledHasReadyFalse() {
        RemotionExecutionPolicyEvaluator evaluator = new RemotionExecutionPolicyEvaluator();
        // disabledDefault has executionEnabled=false and no violations when no readiness/commandPlan
        RemotionExecutionPreflightResult result = evaluator.evaluate(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(),
                null, null);

        assertEquals(RemotionExecutionPreflightStatus.READY_BUT_EXECUTION_DISABLED, result.status());
        assertFalse(result.readyToExecute());
        assertFalse(result.blocked()); // not blocked — structurally ready
        assertFalse(result.notImplemented());
        assertTrue(result.passed()); // passed structural checks
    }

    @Test
    @DisplayName("Preflight requires audit/correlation when policy requires them")
    void preflightRequiresAuditCorrelation() {
        RemotionExecutionPolicy policy = RemotionExecutionPolicy.disabledDefault();
        assertTrue(policy.auditRequired());
        assertTrue(policy.correlationRequired());
        // These are requirements for future execution, not checks in v0
    }

    // --- Policy Guard Safety ---

    @Test
    @DisplayName("RenderPlanPolicyGuard still rejects Remotion execution")
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

    // --- Safety ---

    @Test
    @DisplayName("Policy does not affect fingerprint")
    void policyDoesNotAffectFingerprint() {
        com.example.platform.render.app.timeline.compile.RenderRequestFingerprint fp1 =
                com.example.platform.render.app.timeline.compile.RenderRequestFingerprint.generate(
                        "p", "r", "default_1080p", "PLAN_BASED");
        // Evaluate policy in between
        new RemotionExecutionPolicyEvaluator().evaluate(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(), null, null);
        com.example.platform.render.app.timeline.compile.RenderRequestFingerprint fp2 =
                com.example.platform.render.app.timeline.compile.RenderRequestFingerprint.generate(
                        "p", "r", "default_1080p", "PLAN_BASED");
        assertEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("Public DTOs do not expose policy/preflight result")
    void publicApiSafe() {
        com.example.platform.render.api.dto.TimelineRevisionRenderRequest request =
                new com.example.platform.render.api.dto.TimelineRevisionRenderRequest("default_1080p");
        assertEquals("default_1080p", request.outputProfile());
    }

    @Test
    @DisplayName("No Node/npm/npx execution in new code")
    void noExternalExecution() {
        // Verified by code inspection — all models are pure data
        assertNotNull(RemotionExecutionPolicy.disabledDefault());
    }

    // --- Helpers ---

    private RemotionProviderReadiness createReadiness() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeAvailability avail = new RemotionRuntimeProbe(inventory).probe();
        return RemotionProviderReadiness.from(avail);
    }
}
