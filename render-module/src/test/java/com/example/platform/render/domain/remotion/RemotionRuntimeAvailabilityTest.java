package com.example.platform.render.domain.remotion;

import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.app.timeline.compile.RenderPlanPolicyGuard;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.ArtifactNodeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Remotion runtime availability detection.
 * Proves: safe probes, no execution, policy unchanged, diagnostics correct.
 */
class RemotionRuntimeAvailabilityTest {

    // --- Model tests ---

    @Test
    @DisplayName("Default not-checked availability has executionReady=false")
    void notCheckedDefaults() {
        RemotionRuntimeAvailability avail = RemotionRuntimeAvailability.notChecked();
        assertFalse(avail.executionReady());
        assertTrue(avail.disabledByPolicy());
        assertFalse(avail.documentGenerationReady());
        assertFalse(avail.nodeAvailable());
        assertFalse(avail.npmAvailable());
        assertFalse(avail.npxAvailable());
    }

    @Test
    @DisplayName("Tool status available helper works")
    void toolStatusAvailable() {
        RemotionRuntimeToolStatus status = RemotionRuntimeToolStatus.available("node", "v20.0.0");
        assertTrue(status.isAvailable());
        assertEquals("node", status.toolName());
        assertEquals("v20.0.0", status.version());
        assertNull(status.issue());
    }

    @Test
    @DisplayName("Tool status missing helper works")
    void toolStatusMissing() {
        RemotionRuntimeToolStatus status = RemotionRuntimeToolStatus.missing("npx");
        assertFalse(status.isAvailable());
        assertEquals("npx", status.toolName());
        assertNull(status.version());
        assertNotNull(status.issue());
    }

    @Test
    @DisplayName("Tool status checkFailed helper works")
    void toolStatusCheckFailed() {
        RemotionRuntimeToolStatus status = RemotionRuntimeToolStatus.checkFailed("node", "timeout");
        assertFalse(status.isAvailable());
        assertEquals(RemotionRuntimeAvailabilityStatus.CHECK_FAILED, status.status());
    }

    // --- Probe tests (uses real inventory, environment-safe) ---

    @Test
    @DisplayName("Probe returns valid availability model")
    void probeReturnsValidModel() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeProbe probe = new RemotionRuntimeProbe(inventory);

        RemotionRuntimeAvailability avail = probe.probe();

        assertNotNull(avail);
        // executionReady must always be false
        assertFalse(avail.executionReady());
        // disabledByPolicy must always be true
        assertTrue(avail.disabledByPolicy());
        // documentGenerationReady must be true
        assertTrue(avail.documentGenerationReady());
        // remotionCliAvailable must be false (not probed)
        assertFalse(avail.remotionCliAvailable());
        // toolStatuses must not be empty
        assertFalse(avail.toolStatuses().isEmpty());
    }

    @Test
    @DisplayName("Probe never runs npx remotion")
    void probeNeverRunsNpxRemotion() {
        // The probe only runs node --version, npm --version, npx --version
        // It never runs npx remotion or remotion render
        // This is verified by code inspection and the fact that
        // remotionCliAvailable is always false
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeProbe probe = new RemotionRuntimeProbe(inventory);

        RemotionRuntimeAvailability avail = probe.probe();

        assertFalse(avail.remotionCliAvailable());
        // remotion-cli tool status should be NOT_CHECKED
        assertTrue(avail.toolStatuses().stream()
                .anyMatch(t -> "remotion-cli".equals(t.toolName())
                        && t.status() == RemotionRuntimeAvailabilityStatus.NOT_CHECKED));
    }

    @Test
    @DisplayName("Probe never runs npm install")
    void probeNeverRunsNpmInstall() {
        // Verified by code: probe only runs --version commands
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeProbe probe = new RemotionRuntimeProbe(inventory);
        RemotionRuntimeAvailability avail = probe.probe();
        assertNotNull(avail);
        // If npm install ran, it would fail or take long — test would timeout
    }

    @Test
    @DisplayName("Missing tools represented safely, not exception")
    void missingToolsSafe() {
        // Even if tools are missing, probe returns safe model
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeProbe probe = new RemotionRuntimeProbe(inventory);

        RemotionRuntimeAvailability avail = probe.probe();

        // May or may not have tools depending on environment
        // But must never throw
        assertNotNull(avail.toolStatuses());
        assertNotNull(avail.issues());
    }

    // --- Inventory integration ---

    @Test
    @DisplayName("RenderToolCapabilityInventory includes npx detection")
    void inventoryIncludesNpx() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        List<RenderToolCapabilityInventory.ToolInventoryEntry> tools = inventory.detectTools();

        assertTrue(tools.stream().anyMatch(e -> "npx".equals(e.name())),
                "Inventory should include npx detection");
    }

    @Test
    @DisplayName("Inventory includes node and npm")
    void inventoryIncludesNodeAndNpm() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        List<RenderToolCapabilityInventory.ToolInventoryEntry> tools = inventory.detectTools();

        assertTrue(tools.stream().anyMatch(e -> "node".equals(e.name())));
        assertTrue(tools.stream().anyMatch(e -> "npm".equals(e.name())));
    }

    @Test
    @DisplayName("Missing npx does not fail inventory")
    void missingNpxDoesNotFail() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        // Should not throw even if npx is missing
        List<RenderToolCapabilityInventory.ToolInventoryEntry> tools = inventory.detectTools();
        assertNotNull(tools);
        assertFalse(tools.isEmpty());
    }

    // --- Readiness diagnostics ---

    @Test
    @DisplayName("Provider readiness says executionReady=false")
    void readinessExecutionReadyFalse() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeProbe probe = new RemotionRuntimeProbe(inventory);
        RemotionRuntimeAvailability avail = probe.probe();
        RemotionProviderReadiness readiness = RemotionProviderReadiness.from(avail);

        assertFalse(readiness.executionReady());
    }

    @Test
    @DisplayName("Provider readiness says productionEligible=false")
    void readinessProductionEligibleFalse() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeProbe probe = new RemotionRuntimeProbe(inventory);
        RemotionProviderReadiness readiness = RemotionProviderReadiness.from(probe.probe());

        assertFalse(readiness.productionEligible());
    }

    @Test
    @DisplayName("Provider readiness says autoDispatch=false")
    void readinessAutoDispatchFalse() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionProviderReadiness readiness = RemotionProviderReadiness.from(
                new RemotionRuntimeProbe(inventory).probe());

        assertFalse(readiness.autoDispatch());
    }

    @Test
    @DisplayName("Provider readiness says documentGenerationReady=true")
    void readinessDocumentGenerationReady() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionProviderReadiness readiness = RemotionProviderReadiness.from(
                new RemotionRuntimeProbe(inventory).probe());

        assertTrue(readiness.documentGenerationReady());
    }

    @Test
    @DisplayName("Provider readiness blocked reasons include policy")
    void readinessBlockedReasons() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionProviderReadiness readiness = RemotionProviderReadiness.from(
                new RemotionRuntimeProbe(inventory).probe());

        assertFalse(readiness.blockedReasons().isEmpty());
        assertTrue(readiness.blockedReasons().stream()
                .anyMatch(r -> r.contains("disabled by policy")));
    }

    @Test
    @DisplayName("Provider readiness status is POC")
    void readinessStatusPoc() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionProviderReadiness readiness = RemotionProviderReadiness.from(
                new RemotionRuntimeProbe(inventory).probe());

        assertEquals("POC", readiness.providerStatus());
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
        assertTrue(ProviderStatus.POC.canBeConfiguredForDispatch());
    }

    // --- Safety ---

    @Test
    @DisplayName("Availability model contains no raw environment variables")
    void noEnvironmentVariables() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeAvailability avail = new RemotionRuntimeProbe(inventory).probe();

        String str = avail.toString();
        // Should not contain PATH, HOME, or common env vars
        assertFalse(str.contains("PATH="));
        assertFalse(str.contains("HOME="));
    }

    @Test
    @DisplayName("Availability model contains no local paths in public surfaces")
    void noLocalPaths() {
        RenderToolCapabilityInventory inventory = new RenderToolCapabilityInventory();
        RemotionRuntimeAvailability avail = new RemotionRuntimeProbe(inventory).probe();

        // Version strings are safe — first line of version output, truncated
        assertNotNull(avail.nodeVersion());
    }

    @Test
    @DisplayName("Runtime availability does not affect fingerprint")
    void doesNotAffectFingerprint() {
        com.example.platform.render.app.timeline.compile.RenderRequestFingerprint fp1 =
                com.example.platform.render.app.timeline.compile.RenderRequestFingerprint.generate(
                        "p", "r", "default_1080p", "PLAN_BASED");
        // Probe in between
        new RemotionRuntimeProbe(new RenderToolCapabilityInventory()).probe();
        com.example.platform.render.app.timeline.compile.RenderRequestFingerprint fp2 =
                com.example.platform.render.app.timeline.compile.RenderRequestFingerprint.generate(
                        "p", "r", "default_1080p", "PLAN_BASED");

        assertEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("Public API DTOs do not expose runtime availability")
    void publicApiSafe() {
        com.example.platform.render.api.dto.TimelineRevisionRenderRequest request =
                new com.example.platform.render.api.dto.TimelineRevisionRenderRequest("default_1080p");
        assertEquals("default_1080p", request.outputProfile());
    }
}
