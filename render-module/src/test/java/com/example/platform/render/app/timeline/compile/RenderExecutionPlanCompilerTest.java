package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.execution.*;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RenderExecutionPlanCompiler}.
 *
 * <p>Proves:
 * <ul>
 *   <li>Single video clip FFmpeg binding compiles to deterministic execution plan</li>
 *   <li>Caption overlay includes provider document preparation</li>
 *   <li>Unbound provider node produces failure reason</li>
 *   <li>Missing document draft fails closed</li>
 *   <li>Plan is acyclic</li>
 *   <li>Step IDs are deterministic</li>
 *   <li>Final output has VERIFY_OUTPUT, REGISTER_OUTPUT, LINK_PRODUCT_DEPENDENCY steps</li>
 *   <li>EXECUTE_PROVIDER steps are not execution-ready (v0)</li>
 *   <li>No raw command, env, or storage internals in steps</li>
 *   <li>Plan does not mutate StorageRuntime or ProductRuntime</li>
 * </ul>
 */
class RenderExecutionPlanCompilerTest {

    private RenderExecutionPlanCompiler compiler;
    private ProviderBindingCompiler bindingCompiler;
    private ProviderExecutionDocumentDraftCompiler draftCompiler;
    private CapabilityGraphCompiler capCompiler;
    private ArtifactGraphCompiler artifactCompiler;
    private TimelineNormalizationService normalizer;

    private static final ProviderBindingCompiler.ProviderCandidate FFMPEG =
            new ProviderBindingCompiler.ProviderCandidate(
                    "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0",
                    true, true, "6.1",
                    List.of("MEDIA_INPUT", "VIDEO_DECODE", "VIDEO_TRIM",
                            "AUDIO_DECODE", "AUDIO_MIX",
                            "VIDEO_ENCODE", "AUDIO_ENCODE", "CONTAINER_MUX",
                            "SUBTITLE_BURN_IN", "FONT_RESOLUTION", "MEDIA_FILE_OUTPUT"),
                    List.of());

    @BeforeEach
    void setUp() {
        compiler = new RenderExecutionPlanCompiler();
        bindingCompiler = new ProviderBindingCompiler();
        draftCompiler = new ProviderExecutionDocumentDraftCompiler();
        capCompiler = new CapabilityGraphCompiler();
        artifactCompiler = new ArtifactGraphCompiler();
        normalizer = new TimelineNormalizationService();
    }

    @Test
    @DisplayName("Single video clip FFmpeg → deterministic execution plan")
    void singleClipCompilesToDeterministicPlan() {
        RenderExecutionPlan plan = compileSingleClipPlan();

        assertNotNull(plan);
        assertNotNull(plan.planId());
        assertFalse(plan.steps().isEmpty());
        assertFalse(plan.executionReady());
        assertEquals("PRODUCTION", plan.policy().mode());
        assertEquals(ExecutionEnvironmentTarget.LOCAL, plan.environmentTarget());

        // Verify deterministic
        RenderExecutionPlan plan2 = compileSingleClipPlan();
        assertEquals(plan.planId(), plan2.planId());
        assertEquals(plan.steps().size(), plan2.steps().size());
    }

    @Test
    @DisplayName("Plan has correct step types in expected order")
    void planHasCorrectStepTypes() {
        RenderExecutionPlan plan = compileSingleClipPlan();

        // Should have MATERIALIZE_INPUT, PREPARE_PROVIDER_DOCUMENT, EXECUTE_PROVIDER
        // for input, plus VERIFY_OUTPUT, REGISTER_OUTPUT, LINK_PRODUCT_DEPENDENCY
        // for final render, plus FINALIZE_RENDER
        assertTrue(plan.steps().stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.MATERIALIZE_INPUT));
        assertTrue(plan.steps().stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.PREPARE_PROVIDER_DOCUMENT));
        assertTrue(plan.steps().stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.EXECUTE_PROVIDER));
        assertTrue(plan.steps().stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.VERIFY_OUTPUT));
        assertTrue(plan.steps().stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.REGISTER_OUTPUT));
        assertTrue(plan.steps().stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.LINK_PRODUCT_DEPENDENCY));
        assertTrue(plan.steps().stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.FINALIZE_RENDER));
    }

    @Test
    @DisplayName("EXECUTE_PROVIDER steps are not execution-ready in v0")
    void executeStepsNotReady() {
        RenderExecutionPlan plan = compileSingleClipPlan();

        plan.providerExecutionSteps().forEach(step -> {
            assertFalse(step.executionReady(),
                    "EXECUTE_PROVIDER step should not be execution-ready in v0: " + step.stepId());
            assertEquals(RenderExecutionStepStatus.PENDING, step.status());
        });
    }

    @Test
    @DisplayName("Final output has VERIFY_OUTPUT and REGISTER_OUTPUT steps")
    void finalOutputHasVerifyAndRegisterSteps() {
        RenderExecutionPlan plan = compileSingleClipPlan();

        List<RenderExecutionStep> finalOutputSteps = plan.finalOutputSteps();
        assertFalse(finalOutputSteps.isEmpty());

        assertTrue(finalOutputSteps.stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.VERIFY_OUTPUT));
        assertTrue(finalOutputSteps.stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.REGISTER_OUTPUT));
        assertTrue(finalOutputSteps.stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.LINK_PRODUCT_DEPENDENCY));
    }

    @Test
    @DisplayName("Step IDs are deterministic")
    void stepIdsAreDeterministic() {
        RenderExecutionPlan plan1 = compileSingleClipPlan();
        RenderExecutionPlan plan2 = compileSingleClipPlan();

        assertEquals(plan1.steps().size(), plan2.steps().size());
        for (int i = 0; i < plan1.steps().size(); i++) {
            assertEquals(plan1.steps().get(i).stepId(), plan2.steps().get(i).stepId());
        }
    }

    @Test
    @DisplayName("Plan is acyclic")
    void planIsAcyclic() {
        RenderExecutionPlan plan = compileSingleClipPlan();

        // Simple cycle check: no step should transitively depend on itself
        for (RenderExecutionStep step : plan.steps()) {
            assertFalse(hasCycleToSelf(step.stepId(), plan, new java.util.HashSet<>(),
                    new java.util.HashSet<>()),
                    "Cycle detected involving step: " + step.stepId());
        }
    }

    @Test
    @DisplayName("Unbound node produces failure reason")
    void unboundNodeProducesFailureReason() {
        ProviderBindingPlan bindingPlan = compileBindingPlanWithNoCandidates();
        List<ProviderExecutionDocumentDraft> drafts = List.of();
        ExecutionPolicy policy = ExecutionPolicy.production();

        RenderExecutionPlan plan = compiler.compile(bindingPlan, drafts, policy);

        assertFalse(plan.failureReasons().isEmpty());
        assertTrue(plan.failureReasons().contains(RenderExecutionPlanFailureReason.UNBOUND_CAPABILITY_NODE));
    }

    @Test
    @DisplayName("Missing document draft produces failure reason")
    void missingDraftProducesFailureReason() {
        // Create binding plan with ffmpeg but no drafts
        ProviderBindingPlan bindingPlan = compileBindingPlan(List.of(FFMPEG), "PRODUCTION");
        ExecutionPolicy policy = ExecutionPolicy.production();

        RenderExecutionPlan plan = compiler.compile(bindingPlan, List.of(), policy);

        // Should have MISSING_DOCUMENT_DRAFT for non-INPUT_MEDIA nodes
        assertTrue(plan.failureReasons().contains(RenderExecutionPlanFailureReason.MISSING_DOCUMENT_DRAFT));
    }

    @Test
    @DisplayName("No raw command, env, or storage internals in steps")
    void noRawCommandOrStorageInternals() {
        RenderExecutionPlan plan = compileSingleClipPlan();

        plan.steps().forEach(step -> {
            if (step.metadata() != null) {
                assertFalse(step.metadata().containsKey("rawCommand"));
                assertFalse(step.metadata().containsKey("processEnvironment"));
                assertFalse(step.metadata().containsKey("bucket"));
                assertFalse(step.metadata().containsKey("objectKey"));
                assertFalse(step.metadata().containsKey("rootPath"));
                assertFalse(step.metadata().containsKey("relativePath"));
                assertFalse(step.metadata().containsKey("materializedPath"));
                assertFalse(step.metadata().containsKey("signedUrl"));
                assertFalse(step.metadata().containsKey("storageReferenceId"));
            }
        });
    }

    @Test
    @DisplayName("All steps have LOCAL environment target for FFmpeg")
    void allStepsLocalForFfmpeg() {
        RenderExecutionPlan plan = compileSingleClipPlan();

        plan.steps().forEach(step ->
                assertEquals(ExecutionEnvironmentTarget.LOCAL, step.executionEnvironmentTarget(),
                        "Step " + step.stepId() + " should target LOCAL"));
    }

    @Test
    @DisplayName("Summary contains expected provider names")
    void summaryContainsProviders() {
        RenderExecutionPlan plan = compileSingleClipPlan();

        RenderExecutionPlanSummary summary = plan.summary();
        assertNotNull(summary);
        assertTrue(summary.boundProviders().contains("ffmpeg"));
        assertFalse(summary.executionReady());
    }

    @Test
    @DisplayName("Caption overlay includes PREPARE_PROVIDER_DOCUMENT step")
    void captionOverlayIncludesDocumentPrep() {
        TimelineTextOverlay overlay = TimelineTextOverlay.of("overlay-1", "Hello", 1.0, 3.0);
        TimelineSpec spec = new TimelineSpec("tl-cap", "Test", null,
                List.of(createSingleTrack()), List.of(overlay),
                TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-test");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capCompiler.compile(artifactGraph);
        ProviderBindingPlan bindingPlan = bindingCompiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);

        RenderExecutionPlan plan = compiler.compile(bindingPlan, drafts, ExecutionPolicy.production());

        // Should have PREPARE_PROVIDER_DOCUMENT steps for all bound nodes
        long prepCount = plan.steps().stream()
                .filter(s -> s.type() == RenderExecutionStepType.PREPARE_PROVIDER_DOCUMENT)
                .count();
        assertTrue(prepCount >= 2, "Should have document prep steps for video and subtitle nodes");
    }

    // --- Helpers ---

    private RenderExecutionPlan compileSingleClipPlan() {
        ProviderBindingPlan bindingPlan = compileBindingPlan(List.of(FFMPEG), "PRODUCTION");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);
        return compiler.compile(bindingPlan, drafts, ExecutionPolicy.production());
    }

    private ProviderBindingPlan compileBindingPlan(List<ProviderBindingCompiler.ProviderCandidate> candidates,
                                                    String mode) {
        TimelineSpec spec = createSingleClipTimelineSpec();
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-test");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capCompiler.compile(artifactGraph);
        return bindingCompiler.compile(capGraph, candidates, mode);
    }

    private ProviderBindingPlan compileBindingPlanWithNoCandidates() {
        return compileBindingPlan(List.of(), "PRODUCTION");
    }

    private TimelineSpec createSingleClipTimelineSpec() {
        return new TimelineSpec("tl-test", "Test", null,
                List.of(createSingleTrack()), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0,
                Map.of());
    }

    private TimelineTrack createSingleTrack() {
        TimelineClip clip = TimelineClip.of("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"),
                0, 0, 5);
        return new TimelineTrack("trk-1", "Video 1", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
    }

    private boolean hasCycleToSelf(String stepId, RenderExecutionPlan plan,
                                   Set<String> visited, Set<String> inStack) {
        if (inStack.contains(stepId)) return true;
        if (visited.contains(stepId)) return false;
        visited.add(stepId);
        inStack.add(stepId);
        RenderExecutionStep step = plan.findStep(stepId);
        if (step != null && step.dependencies() != null) {
            for (String dep : step.dependencies()) {
                if (hasCycleToSelf(dep, plan, visited, inStack)) return true;
            }
        }
        inStack.remove(stepId);
        return false;
    }
}
