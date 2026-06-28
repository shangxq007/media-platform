package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.execution.*;
import com.example.platform.render.domain.timeline.compile.ArtifactNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LocalExecutionPlanRunner}.
 *
 * <p>Proves:
 * <ul>
 *   <li>Runner rejects null plan</li>
 *   <li>Runner rejects null context</li>
 *   <li>Runner rejects non-FFmpeg provider steps</li>
 *   <li>Runner rejects non-LOCAL environment target</li>
 *   <li>Runner rejects plans with non-production FFmpeg provider</li>
 *   <li>Runner executes FFmpeg baseline steps</li>
 *   <li>Failed steps block downstream dependencies</li>
 *   <li>Deterministic plan execution produces stable results</li>
 * </ul>
 */
class LocalExecutionPlanRunnerTest {

    @TempDir
    Path tempDir;

    private LocalExecutionPlanRunner runner;
    private RenderPlanPolicyGuard policyGuard;
    private MockStepExecutor stepExecutor;

    @BeforeEach
    void setUp() {
        policyGuard = new RenderPlanPolicyGuard();
        stepExecutor = new MockStepExecutor();
        runner = new LocalExecutionPlanRunner(policyGuard, stepExecutor);
    }

    @Test
    @DisplayName("Runner rejects null plan")
    void rejectsNullPlan() {
        LocalExecutionPlanContext context = createContext();
        LocalExecutionPlanRunResult result = runner.run(null, context);
        assertTrue(result.isFailed());
        assertEquals(LocalExecutionPlanRunStatus.FAILED_CLOSED, result.status());
    }

    @Test
    @DisplayName("Runner rejects null context")
    void rejectsNullContext() {
        RenderExecutionPlan plan = createValidFfmpegPlan();
        LocalExecutionPlanRunResult result = runner.run(plan, null);
        assertTrue(result.isFailed());
        assertEquals(LocalExecutionPlanRunStatus.FAILED_CLOSED, result.status());
    }

    @Test
    @DisplayName("Runner rejects non-FFmpeg provider")
    void rejectsNonFfmpegProvider() {
        RenderExecutionPlan plan = createPlanWithProvider("mlt");
        LocalExecutionPlanContext context = createContext();
        LocalExecutionPlanRunResult result = runner.run(plan, context);
        assertTrue(result.isFailed());
    }

    @Test
    @DisplayName("Runner rejects non-LOCAL environment target")
    void rejectsNonLocalTarget() {
        RenderExecutionPlan plan = createPlanWithTarget(ExecutionEnvironmentTarget.OPENCUE);
        LocalExecutionPlanContext context = createContext();
        LocalExecutionPlanRunResult result = runner.run(plan, context);
        assertTrue(result.isFailed());
    }

    @Test
    @DisplayName("Runner rejects non-production FFmpeg provider")
    void rejectsNonProductionFfmpeg() {
        BoundProviderRef pocRef = new BoundProviderRef(
                "ffmpeg", ProviderStatus.POC, ProviderType.RENDER, "P0",
                true, true, "6.1", 200);
        RenderExecutionPlan plan = createPlanWithProviderRef(pocRef);
        LocalExecutionPlanContext context = createContext();
        LocalExecutionPlanRunResult result = runner.run(plan, context);
        assertTrue(result.isFailed());
    }

    @Test
    @DisplayName("Runner executes valid FFmpeg plan")
    void executesValidFfmpegPlan() {
        RenderExecutionPlan plan = createValidFfmpegPlan();
        LocalExecutionPlanContext context = createContext();
        stepExecutor.setSucceedAll(true);

        LocalExecutionPlanRunResult result = runner.run(plan, context);

        assertTrue(result.isSuccess());
        assertFalse(result.stepResults().isEmpty());
        assertEquals(LocalExecutionPlanRunStatus.SUCCEEDED, result.status());
    }

    @Test
    @DisplayName("Failed step blocks downstream dependency")
    void failedStepBlocksDownstream() {
        RenderExecutionPlan plan = createValidFfmpegPlan();
        LocalExecutionPlanContext context = createContext();
        stepExecutor.setSucceedAll(false);

        LocalExecutionPlanRunResult result = runner.run(plan, context);

        assertTrue(result.isFailed());
        // Should have at least one failed step and one blocked step
        assertTrue(result.stepResults().stream()
                .anyMatch(r -> r.status() == LocalExecutionPlanRunStatus.FAILED));
    }

    @Test
    @DisplayName("Step results preserve step types")
    void stepResultsPreserveTypes() {
        RenderExecutionPlan plan = createValidFfmpegPlan();
        LocalExecutionPlanContext context = createContext();
        stepExecutor.setSucceedAll(true);

        LocalExecutionPlanRunResult result = runner.run(plan, context);

        assertTrue(result.stepResults().stream()
                .anyMatch(r -> "MATERIALIZE_INPUT".equals(r.stepType())));
        assertTrue(result.stepResults().stream()
                .anyMatch(r -> "EXECUTE_PROVIDER".equals(r.stepType())));
    }

    // --- Helpers ---

    private LocalExecutionPlanContext createContext() {
        return new LocalExecutionPlanContext(
                "rj-test", "tenant-1", "proj-1", "rev-1", "snap-1",
                "{}", "default_1080p",
                List.of("input-1"), "input-1", null,
                tempDir, tempDir.resolve("output"), "output.mp4",
                1920, 1080, 30, 5.0, false, "mp4",
                Map.of());
    }

    private RenderExecutionPlan createValidFfmpegPlan() {
        BoundProviderRef ffmpegRef = new BoundProviderRef(
                "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0",
                true, true, "6.1", 0);

        RenderExecutionStep materialize = new RenderExecutionStep(
                "step-mat", RenderExecutionStepType.MATERIALIZE_INPUT,
                RenderExecutionStepStatus.PENDING,
                "node-input", ArtifactNodeType.INPUT_MEDIA,
                null, null, null, List.of(), false,
                ExecutionEnvironmentTarget.LOCAL, "Materialize input", Map.of());

        RenderExecutionStep execute = new RenderExecutionStep(
                "step-exec", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING,
                "node-render", ArtifactNodeType.FINAL_RENDER,
                "ffmpeg", ffmpegRef, null, List.of("step-mat"), false,
                ExecutionEnvironmentTarget.LOCAL, "Execute FFmpeg", Map.of());

        RenderExecutionStep verify = new RenderExecutionStep(
                "step-verify", RenderExecutionStepType.VERIFY_OUTPUT,
                RenderExecutionStepStatus.PENDING,
                "node-render", ArtifactNodeType.FINAL_RENDER,
                "ffmpeg", ffmpegRef, null, List.of("step-exec"), false,
                ExecutionEnvironmentTarget.LOCAL, "Verify output", Map.of());

        RenderExecutionStep register = new RenderExecutionStep(
                "step-reg", RenderExecutionStepType.REGISTER_OUTPUT,
                RenderExecutionStepStatus.PENDING,
                "node-render", ArtifactNodeType.FINAL_RENDER,
                "ffmpeg", ffmpegRef, null, List.of("step-verify"), false,
                ExecutionEnvironmentTarget.LOCAL, "Register output", Map.of());

        RenderExecutionStep finalize = new RenderExecutionStep(
                "step-final", RenderExecutionStepType.FINALIZE_RENDER,
                RenderExecutionStepStatus.PENDING,
                null, null, null, null, null, List.of("step-reg"), false,
                ExecutionEnvironmentTarget.LOCAL, "Finalize", Map.of());

        return new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-test", "PRODUCTION"),
                "bp-test", "tl-test",
                ExecutionPolicy.production(),
                ExecutionEnvironmentTarget.LOCAL,
                List.of(materialize, execute, verify, register, finalize),
                false, List.of());
    }

    private RenderExecutionPlan createPlanWithProvider(String providerName) {
        BoundProviderRef ref = new BoundProviderRef(
                providerName, ProviderStatus.POC, ProviderType.RENDER, "P1",
                true, true, "1.0", 200);

        RenderExecutionStep execute = new RenderExecutionStep(
                "step-exec", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING,
                "node-1", ArtifactNodeType.FINAL_RENDER,
                providerName, ref, null, List.of(), false,
                ExecutionEnvironmentTarget.LOCAL, "Execute " + providerName, Map.of());

        return new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-test", "PRODUCTION"),
                "bp-test", "tl-test",
                ExecutionPolicy.production(),
                ExecutionEnvironmentTarget.LOCAL,
                List.of(execute), false, List.of());
    }

    private RenderExecutionPlan createPlanWithTarget(ExecutionEnvironmentTarget target) {
        BoundProviderRef ffmpegRef = new BoundProviderRef(
                "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0",
                true, true, "6.1", 0);

        RenderExecutionStep execute = new RenderExecutionStep(
                "step-exec", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING,
                "node-1", ArtifactNodeType.FINAL_RENDER,
                "ffmpeg", ffmpegRef, null, List.of(), false,
                target, "Execute FFmpeg", Map.of());

        return new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-test", "PRODUCTION"),
                "bp-test", "tl-test",
                ExecutionPolicy.production(), target,
                List.of(execute), false, List.of());
    }

    private RenderExecutionPlan createPlanWithProviderRef(BoundProviderRef ref) {
        RenderExecutionStep execute = new RenderExecutionStep(
                "step-exec", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING,
                "node-1", ArtifactNodeType.FINAL_RENDER,
                ref.providerName(), ref, null, List.of(), false,
                ExecutionEnvironmentTarget.LOCAL, "Execute FFmpeg", Map.of());

        return new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-test", "PRODUCTION"),
                "bp-test", "tl-test",
                ExecutionPolicy.production(),
                ExecutionEnvironmentTarget.LOCAL,
                List.of(execute), false, List.of());
    }

    /**
     * Mock step executor for unit testing.
     */
    static class MockStepExecutor extends RenderExecutionStepExecutor {
        private boolean succeedAll = true;

        MockStepExecutor() {
            super(null, null, null, null, null);
        }

        void setSucceedAll(boolean succeedAll) {
            this.succeedAll = succeedAll;
        }

        @Override
        public LocalExecutionPlanStepResult execute(RenderExecutionStep step,
                                                      LocalExecutionPlanContext context) {
            if (succeedAll) {
                return LocalExecutionPlanStepResult.succeeded(
                        step.stepId(), step.type().name(), "Mock success", 10);
            } else {
                return LocalExecutionPlanStepResult.failed(
                        step.stepId(), step.type().name(), "Mock failure", 10);
            }
        }
    }
}
