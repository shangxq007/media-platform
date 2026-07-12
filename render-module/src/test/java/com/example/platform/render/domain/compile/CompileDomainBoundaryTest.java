package com.example.platform.render.domain.timeline.compile;

import com.example.platform.render.domain.timeline.compile.binding.BoundProviderRef;
import com.example.platform.render.domain.timeline.compile.binding.ProviderBindingDecision;
import com.example.platform.render.domain.timeline.compile.binding.ProviderBindingNode;
import com.example.platform.render.domain.timeline.compile.binding.ProviderBindingPlan;
import com.example.platform.render.domain.timeline.compile.binding.ProviderBindingPlanId;
import com.example.platform.render.domain.timeline.compile.binding.ProviderBindingStatus;
import com.example.platform.render.domain.timeline.compile.binding.ProviderBindingFailureReason;
import com.example.platform.render.domain.timeline.compile.executionplan.ExecutionEnvironmentTarget;
import com.example.platform.render.domain.timeline.compile.executionplan.ExecutionPolicy;
import com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionPlan;
import com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionStep;
import com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionStepStatus;
import com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionStepType;
import com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionPlanId;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Domain-level tests for the compile pipeline contract.
 *
 * <p>Tests the contract of ProviderBindingPlan and RenderExecutionPlan
 * without requiring database or external dependencies.
 *
 * <p>These tests validate that the domain types compose correctly and
 * maintain their invariants — the foundation for the VS.0 vertical slice
 * compile pipeline.
 */
class CompileDomainBoundaryTest {

    // ==================== ProviderBindingPlan Contract ====================

    @Nested
    @DisplayName("ProviderBindingPlan Contract")
    class ProviderBindingPlanContract {

        @Test
        @DisplayName("Binding plan with all BOUND nodes reports allBound=true")
        void bindingPlanAllBound() {
            BoundProviderRef ffmpegRef = new BoundProviderRef(
                    "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER,
                    "P0", true, true, "6.0", 0);

            ProviderBindingNode inputNode = new ProviderBindingNode(
                    "node-input", ArtifactNodeType.INPUT_MEDIA, "Input Media",
                    List.of("demux"),
                    new ProviderBindingDecision("node-input", "INPUT_MEDIA",
                            List.of("demux"), ProviderBindingStatus.BOUND,
                            ffmpegRef, List.of(ffmpegRef), null, "FFmpeg selected"));

            ProviderBindingNode finalNode = new ProviderBindingNode(
                    "node-final", ArtifactNodeType.FINAL_RENDER, "Final Render",
                    List.of("transcode", "mux"),
                    new ProviderBindingDecision("node-final", "FINAL_RENDER",
                            List.of("transcode", "mux"), ProviderBindingStatus.BOUND,
                            ffmpegRef, List.of(ffmpegRef), null, "FFmpeg selected"));

            ProviderBindingPlan plan = new ProviderBindingPlan(
                    ProviderBindingPlanId.fromCapabilityGraphId("graph-all-bound"),
                    "graph-all-bound",
                    "tl-bound",
                    List.of(inputNode, finalNode),
                    List.of(),
                    "PRODUCTION",
                    true, false);

            assertTrue(plan.allBound());
            assertFalse(plan.hasFailures());
            assertEquals(2, plan.boundNodes().size());
            assertEquals(0, plan.failedNodes().size());
            assertNotNull(plan.finalRenderNode());
            assertEquals("ffmpeg", plan.finalRenderNode().boundProviderName());
        }

        @Test
        @DisplayName("Binding plan with UNSUPPORTED node reports hasFailures=true")
        void bindingPlanWithFailures() {
            ProviderBindingNode unsupportedNode = new ProviderBindingNode(
                    "node-3d", ArtifactNodeType.FINAL_RENDER, "3D Render",
                    List.of("3d_render"),
                    new ProviderBindingDecision("node-3d", "FINAL_RENDER",
                            List.of("3d_render"), ProviderBindingStatus.UNSUPPORTED,
                            null, List.of(),
                            ProviderBindingFailureReason.REQUIRED_CAPABILITY_MISSING,
                            "No provider supports 3d_render"));

            ProviderBindingPlan plan = new ProviderBindingPlan(
                    ProviderBindingPlanId.fromCapabilityGraphId("graph-fail"),
                    "graph-fail", "tl-fail",
                    List.of(unsupportedNode), List.of(),
                    "PRODUCTION", false, true);

            assertFalse(plan.allBound());
            assertTrue(plan.hasFailures());
            assertEquals(1, plan.failedNodes().size());
            assertNull(plan.failedNodes().get(0).boundProviderName());
        }

        @Test
        @DisplayName("ProviderBindingPlanId is deterministic and stable")
        void bindingPlanIdDeterministic() {
            String graphId = "graph-deterministic-test";
            ProviderBindingPlanId id1 = ProviderBindingPlanId.fromCapabilityGraphId(graphId);
            ProviderBindingPlanId id2 = ProviderBindingPlanId.fromCapabilityGraphId(graphId);
            ProviderBindingPlanId id3 = ProviderBindingPlanId.fromCapabilityGraphId("graph-other");

            assertEquals(id1.value(), id2.value());
            assertNotEquals(id1.value(), id3.value());
            assertTrue(id1.value().startsWith("pbp-"));
        }
    }

    // ==================== RenderExecutionPlan Contract ====================

    @Nested
    @DisplayName("RenderExecutionPlan Contract")
    class RenderExecutionPlanContract {

        @Test
        @DisplayName("Execution plan groups steps by type correctly")
        void executionPlanGroupsSteps() {
            RenderExecutionStep materialize = new RenderExecutionStep(
                    "s1", RenderExecutionStepType.MATERIALIZE_INPUT,
                    RenderExecutionStepStatus.PENDING,
                    "node-input", ArtifactNodeType.INPUT_MEDIA,
                    null, null, null, List.of(), false,
                    ExecutionEnvironmentTarget.LOCAL, "Materialize", Map.of());

            RenderExecutionStep prepDoc = new RenderExecutionStep(
                    "s2", RenderExecutionStepType.PREPARE_PROVIDER_DOCUMENT,
                    RenderExecutionStepStatus.PENDING,
                    "node-final", ArtifactNodeType.FINAL_RENDER,
                    null, null, null, List.of("s1"), false,
                    ExecutionEnvironmentTarget.LOCAL, "Prepare document", Map.of());

            RenderExecutionStep executeProvider = new RenderExecutionStep(
                    "s3", RenderExecutionStepType.EXECUTE_PROVIDER,
                    RenderExecutionStepStatus.PENDING,
                    "node-final", ArtifactNodeType.FINAL_RENDER,
                    "ffmpeg", null, null, List.of("s2"), false,
                    ExecutionEnvironmentTarget.LOCAL, "Execute FFmpeg", Map.of());

            RenderExecutionStep registerOutput = new RenderExecutionStep(
                    "s4", RenderExecutionStepType.REGISTER_OUTPUT,
                    RenderExecutionStepStatus.PENDING,
                    null, null, null, null, null, List.of("s3"), false,
                    ExecutionEnvironmentTarget.LOCAL, "Register", Map.of());

            RenderExecutionStep finalize = new RenderExecutionStep(
                    "s5", RenderExecutionStepType.FINALIZE_RENDER,
                    RenderExecutionStepStatus.PENDING,
                    null, null, null, null, null, List.of("s4"), false,
                    ExecutionEnvironmentTarget.LOCAL, "Finalize", Map.of());

            RenderExecutionPlan plan = new RenderExecutionPlan(
                    new RenderExecutionPlanId("ep-group-test"),
                    "pbp-test", "tl-test",
                    ExecutionPolicy.production(), ExecutionEnvironmentTarget.LOCAL,
                    List.of(materialize, prepDoc, executeProvider, registerOutput, finalize),
                    false, List.of());

            assertEquals(1, plan.materializationSteps().size());
            assertEquals(1, plan.providerExecutionSteps().size());
            assertEquals(0, plan.finalOutputSteps().size());
            assertEquals(1, plan.finalizationSteps().size());
            assertEquals(5, plan.steps().size());
        }

        @Test
        @DisplayName("Execution plan summary lists unique providers")
        void executionPlanSummaryProviders() {
            RenderExecutionStep ffmpeg1 = new RenderExecutionStep(
                    "s1", RenderExecutionStepType.EXECUTE_PROVIDER,
                    RenderExecutionStepStatus.PENDING,
                    "node-1", ArtifactNodeType.TRIMMED_MEDIA,
                    "ffmpeg", null, null, List.of(), false,
                    ExecutionEnvironmentTarget.LOCAL, "Trim", Map.of());

            RenderExecutionStep ffmpeg2 = new RenderExecutionStep(
                    "s2", RenderExecutionStepType.EXECUTE_PROVIDER,
                    RenderExecutionStepStatus.PENDING,
                    "node-2", ArtifactNodeType.FINAL_RENDER,
                    "ffmpeg", null, null, List.of("s1"), false,
                    ExecutionEnvironmentTarget.LOCAL, "Encode", Map.of());

            RenderExecutionPlan plan = new RenderExecutionPlan(
                    new RenderExecutionPlanId("ep-summary"),
                    "pbp-summary", "tl-summary",
                    ExecutionPolicy.production(), ExecutionEnvironmentTarget.LOCAL,
                    List.of(ffmpeg1, ffmpeg2), false, List.of());

            var summary = plan.summary();
            assertEquals(1, summary.boundProviders().size(), "Should deduplicate provider names");
            assertTrue(summary.boundProviders().contains("ffmpeg"));
        }

        @Test
        @DisplayName("Execution plan with failures reports failed steps")
        void executionPlanWithFailures() {
            RenderExecutionStep failedStep = new RenderExecutionStep(
                    "s-fail", RenderExecutionStepType.EXECUTE_PROVIDER,
                    RenderExecutionStepStatus.FAILED,
                    "node-final", ArtifactNodeType.FINAL_RENDER,
                    "ffmpeg", null, null, List.of(), false,
                    ExecutionEnvironmentTarget.LOCAL, "FFmpeg (failed)",
                    Map.of());

            RenderExecutionStep blockedStep = new RenderExecutionStep(
                    "s-blocked", RenderExecutionStepType.REGISTER_OUTPUT,
                    RenderExecutionStepStatus.BLOCKED,
                    null, null, null, null, null, List.of("s-fail"), false,
                    ExecutionEnvironmentTarget.LOCAL, "Register (blocked)",
                    Map.of());

            RenderExecutionPlan plan = new RenderExecutionPlan(
                    new RenderExecutionPlanId("ep-fail"),
                    "pbp-fail", "tl-fail",
                    ExecutionPolicy.production(), ExecutionEnvironmentTarget.LOCAL,
                    List.of(failedStep, blockedStep), false, List.of());

            assertEquals(1, plan.failedSteps().size());
            assertEquals(1, plan.blockedSteps().size());
        }

        @Test
        @DisplayName("Find step by ID returns correct step")
        void findStepById() {
            RenderExecutionStep step = new RenderExecutionStep(
                    "step-lookup", RenderExecutionStepType.MATERIALIZE_INPUT,
                    RenderExecutionStepStatus.PENDING,
                    null, null, null, null, null, List.of(), false,
                    ExecutionEnvironmentTarget.LOCAL, "Lookup test", Map.of());

            RenderExecutionPlan plan = new RenderExecutionPlan(
                    new RenderExecutionPlanId("ep-lookup"),
                    "pbp-lookup", "tl-lookup",
                    ExecutionPolicy.production(), ExecutionEnvironmentTarget.LOCAL,
                    List.of(step), false, List.of());

            assertNotNull(plan.findStep("step-lookup"));
            assertNull(plan.findStep("nonexistent"));
        }
    }

    // ==================== ExecutionPolicy Contract ====================

    @Nested
    @DisplayName("ExecutionPolicy Contract")
    class ExecutionPolicyContract {

        @Test
        @DisplayName("Production policy is restrictive")
        void productionPolicy() {
            ExecutionPolicy policy = ExecutionPolicy.production();
            assertEquals("PRODUCTION", policy.mode());
            assertFalse(policy.allowManualProviders());
            assertFalse(policy.allowExperimentalProviders());
            assertFalse(policy.allowOpenCueSubmit());
            assertFalse(policy.allowProviderExecution());
        }

        @Test
        @DisplayName("Manual policy allows POC providers")
        void manualPolicy() {
            ExecutionPolicy policy = ExecutionPolicy.manual();
            assertEquals("MANUAL", policy.mode());
            assertTrue(policy.allowManualProviders());
            assertFalse(policy.allowExperimentalProviders());
        }

        @Test
        @DisplayName("Experiment policy allows all configurable providers")
        void experimentPolicy() {
            ExecutionPolicy policy = ExecutionPolicy.experiment();
            assertEquals("EXPERIMENT", policy.mode());
            assertTrue(policy.allowManualProviders());
            assertTrue(policy.allowExperimentalProviders());
        }
    }
}
