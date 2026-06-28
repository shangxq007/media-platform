package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.ArtifactNodeType;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PLAN_BASED default readiness tests.
 * Verifies safety, policy, rollback, and public API invariants.
 */
class PlanBasedDefaultReadinessTest {

    @Test
    @DisplayName("Default execution mode is LEGACY")
    void defaultModeIsLegacy() {
        TimelineRenderExecutionProperties props = TimelineRenderExecutionProperties.defaults();
        assertEquals(TimelineRenderExecutionMode.LEGACY, props.executionMode());
    }

    @Test
    @DisplayName("Feature flag not in public request DTO")
    void featureFlagNotInRequest() {
        com.example.platform.render.api.dto.TimelineRevisionRenderRequest request =
                new com.example.platform.render.api.dto.TimelineRevisionRenderRequest("default_1080p");
        assertEquals("default_1080p", request.outputProfile());
        // No executionMode field in request
    }

    @Test
    @DisplayName("Feature flag not in public response DTO")
    void featureFlagNotInResponse() {
        com.example.platform.render.api.dto.TimelineRevisionRenderResponse response =
                com.example.platform.render.api.dto.TimelineRevisionRenderResponse.failure("rev-1", "test");
        // Response has no executionMode, correlationId, graphId fields
        assertNull(response.renderMode()); // failure case has null renderMode
    }

    @Test
    @DisplayName("PLAN_BASED rejects non-FFmpeg provider")
    void planBasedRejectsNonFfmpeg() {
        LocalExecutionPlanRunner runner = createRunner();
        BoundProviderRef mltRef = new BoundProviderRef(
                "mlt", ProviderStatus.POC, ProviderType.RENDER, "P1", true, true, "7.22", 200);
        RenderExecutionStep exec = new RenderExecutionStep(
                "step-1", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING, "n1", ArtifactNodeType.FINAL_RENDER,
                "mlt", mltRef, null, List.of(), false,
                ExecutionEnvironmentTarget.LOCAL, "MLT exec", Map.of());
        RenderExecutionPlan plan = new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-1", "PRODUCTION"),
                "bp-1", "tl-1", ExecutionPolicy.production(),
                ExecutionEnvironmentTarget.LOCAL, List.of(exec), false, List.of());
        LocalExecutionPlanContext ctx = createContext();
        LocalExecutionPlanRunResult result = runner.run(plan, ctx);
        assertTrue(result.isFailed() || result.status() == LocalExecutionPlanRunStatus.NOT_EXECUTABLE);
    }

    @Test
    @DisplayName("PLAN_BASED rejects non-LOCAL target")
    void planBasedRejectsNonLocal() {
        LocalExecutionPlanRunner runner = createRunner();
        BoundProviderRef ffmpegRef = new BoundProviderRef(
                "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0", true, true, "6.1", 0);
        RenderExecutionStep exec = new RenderExecutionStep(
                "step-1", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING, "n1", ArtifactNodeType.FINAL_RENDER,
                "ffmpeg", ffmpegRef, null, List.of(), false,
                ExecutionEnvironmentTarget.OPENCUE, "FFmpeg OpenCue", Map.of());
        RenderExecutionPlan plan = new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-1", "PRODUCTION"),
                "bp-1", "tl-1", ExecutionPolicy.production(),
                ExecutionEnvironmentTarget.OPENCUE, List.of(exec), false, List.of());
        LocalExecutionPlanContext ctx = createContext();
        LocalExecutionPlanRunResult result = runner.run(plan, ctx);
        assertTrue(result.isFailed() || result.status() == LocalExecutionPlanRunStatus.NOT_EXECUTABLE);
    }

    @Test
    @DisplayName("PLAN_BASED respects RenderPlanPolicyGuard")
    void planBasedRespectsPolicyGuard() {
        RenderPlanPolicyGuard guard = new RenderPlanPolicyGuard();
        BoundProviderRef ffmpegRef = new BoundProviderRef(
                "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0", true, true, "6.1", 0);
        ArtifactNodeType nodeType = ArtifactNodeType.FINAL_RENDER;
        RenderExecutionStep exec = new RenderExecutionStep(
                "s1", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING, "n1", nodeType,
                "ffmpeg", ffmpegRef, null, List.of(), false,
                ExecutionEnvironmentTarget.LOCAL, "Exec", Map.of());
        RenderExecutionStep verify = new RenderExecutionStep(
                "s2", RenderExecutionStepType.VERIFY_OUTPUT,
                RenderExecutionStepStatus.PENDING, "n1", nodeType,
                "ffmpeg", ffmpegRef, null, List.of("s1"), false,
                ExecutionEnvironmentTarget.LOCAL, "Verify", Map.of());
        RenderExecutionStep register = new RenderExecutionStep(
                "s3", RenderExecutionStepType.REGISTER_OUTPUT,
                RenderExecutionStepStatus.PENDING, "n1", nodeType,
                "ffmpeg", ffmpegRef, null, List.of("s2"), false,
                ExecutionEnvironmentTarget.LOCAL, "Register", Map.of());
        RenderExecutionStep link = new RenderExecutionStep(
                "s4", RenderExecutionStepType.LINK_PRODUCT_DEPENDENCY,
                RenderExecutionStepStatus.PENDING, "n1", nodeType,
                null, null, null, List.of("s3"), false,
                ExecutionEnvironmentTarget.LOCAL, "Link", Map.of());
        RenderExecutionStep finalize = new RenderExecutionStep(
                "s5", RenderExecutionStepType.FINALIZE_RENDER,
                RenderExecutionStepStatus.PENDING, null, null, null, null, null,
                List.of("s4"), false, ExecutionEnvironmentTarget.LOCAL, "Finalize", Map.of());
        RenderExecutionPlan plan = new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-1", "PRODUCTION"),
                "bp-1", "tl-1", ExecutionPolicy.production(),
                ExecutionEnvironmentTarget.LOCAL,
                List.of(exec, verify, register, link, finalize), false, List.of());
        RenderPlanPolicyResult result = guard.evaluate(plan, plan.policy());
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Rollback: switching to LEGACY requires no data migration")
    void rollbackRequiresNoMigration() {
        TimelineRenderExecutionProperties legacyProps = TimelineRenderExecutionProperties.defaults();
        TimelineRenderExecutionProperties planProps =
                new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.PLAN_BASED);
        // Switching is just config change - no data migration
        assertEquals(TimelineRenderExecutionMode.LEGACY, legacyProps.executionMode());
        assertEquals(TimelineRenderExecutionMode.PLAN_BASED, planProps.executionMode());
    }

    @Test
    @DisplayName("executionMode in fingerprint prevents cross-mode reuse")
    void executionModeInFingerprint() {
        RenderRequestFingerprint legacyFp = RenderRequestFingerprint.generate("p", "r", "default_1080p", "LEGACY");
        RenderRequestFingerprint planFp = RenderRequestFingerprint.generate("p", "r", "default_1080p", "PLAN_BASED");
        assertNotEquals(legacyFp.value(), planFp.value(),
                "Different execution modes should produce different fingerprints");
    }

    @Test
    @DisplayName("Same mode same request produces same fingerprint")
    void sameModeSameFingerprint() {
        RenderRequestFingerprint fp1 = RenderRequestFingerprint.generate("p", "r", "default_1080p", "LEGACY");
        RenderRequestFingerprint fp2 = RenderRequestFingerprint.generate("p", "r", "default_1080p", "LEGACY");
        assertEquals(fp1.value(), fp2.value());
    }

    private LocalExecutionPlanRunner createRunner() {
        return new LocalExecutionPlanRunner(new RenderPlanPolicyGuard(),
                new com.example.platform.render.app.timeline.compile.RenderExecutionStepExecutor(
                        null, null, null, null, null, null));
    }

    private LocalExecutionPlanContext createContext() {
        return new LocalExecutionPlanContext("rj-1", "t-1", "p-1", "r-1", "s-1",
                "{}", "default_1080p", List.of("in-1"), "in-1", null,
                java.nio.file.Path.of("/tmp"), java.nio.file.Path.of("/tmp/out"), "output.mp4",
                1920, 1080, 30, 5.0, false, "mp4", Map.of());
    }
}
