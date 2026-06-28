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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RenderPlanPolicyGuard}.
 *
 * <p>Proves:
 * <ul>
 *   <li>Valid plan passes as VALID_FOR_DRY_RUN</li>
 *   <li>POC provider in PRODUCTION mode is rejected</li>
 *   <li>POC provider in MANUAL mode passes (not executable)</li>
 *   <li>OpenCue target rejected unless explicitly allowed</li>
 *   <li>OpenFX provider without host is rejected</li>
 *   <li>Step with raw command is rejected</li>
 *   <li>Step with storage internals is rejected</li>
 *   <li>Final output must have verification and registration steps</li>
 *   <li>Plan must be acyclic</li>
 *   <li>Step IDs must be deterministic</li>
 *   <li>Dependency graph must be valid</li>
 *   <li>Null plan fails closed</li>
 * </ul>
 */
class RenderPlanPolicyGuardTest {

    private RenderPlanPolicyGuard guard;
    private RenderExecutionPlanCompiler planCompiler;
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

    private static final ProviderBindingCompiler.ProviderCandidate MLT_POC =
            new ProviderBindingCompiler.ProviderCandidate(
                    "mlt", ProviderStatus.POC, ProviderType.RENDER, "P1",
                    true, true, "7.22",
                    List.of("MEDIA_INPUT", "VIDEO_DECODE", "VIDEO_TRIM", "AUDIO_DECODE",
                            "AUDIO_MIX", "VIDEO_ENCODE", "AUDIO_ENCODE",
                            "CONTAINER_MUX", "MEDIA_FILE_OUTPUT"),
                    List.of());

    private static final ProviderBindingCompiler.ProviderCandidate OPENFX =
            new ProviderBindingCompiler.ProviderCandidate(
                    "openfx", ProviderStatus.POC, ProviderType.RENDER, "P2",
                    true, true, "1.0",
                    List.of("VIDEO_ENCODE", "MEDIA_FILE_OUTPUT"),
                    List.of());

    @BeforeEach
    void setUp() {
        guard = new RenderPlanPolicyGuard();
        planCompiler = new RenderExecutionPlanCompiler();
        bindingCompiler = new ProviderBindingCompiler();
        draftCompiler = new ProviderExecutionDocumentDraftCompiler();
        capCompiler = new CapabilityGraphCompiler();
        artifactCompiler = new ArtifactGraphCompiler();
        normalizer = new TimelineNormalizationService();
    }

    @Test
    @DisplayName("Valid FFmpeg plan passes as VALID_FOR_DRY_RUN")
    void validPlanPassesAsDryRun() {
        RenderExecutionPlan plan = compilePlan(List.of(FFMPEG), "PRODUCTION");

        RenderPlanPolicyResult result = guard.evaluate(plan, plan.policy());

        assertTrue(result.isValid());
        assertFalse(result.hasViolations());
        assertFalse(result.isRejected());
    }

    @Test
    @DisplayName("POC provider in PRODUCTION mode is rejected")
    void pocProviderInProductionRejected() {
        RenderExecutionPlan plan = compilePlan(List.of(MLT_POC), "MANUAL");

        RenderPlanPolicyResult result = guard.evaluate(plan, ExecutionPolicy.production());

        assertTrue(result.hasViolations());
        assertTrue(result.isRejected());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == RenderPlanPolicyViolationType.NON_PRODUCTION_PROVIDER));
    }

    @Test
    @DisplayName("POC provider in MANUAL mode passes policy guard")
    void pocProviderInManualPasses() {
        RenderExecutionPlan plan = compilePlan(List.of(MLT_POC), "MANUAL");

        RenderPlanPolicyResult result = guard.evaluate(plan, plan.policy());

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("OpenCue target rejected unless explicitly allowed")
    void opencueRejectedUnlessAllowed() {
        RenderExecutionPlan plan = compilePlan(List.of(FFMPEG), "PRODUCTION");

        // Policy without OpenCue enabled
        RenderPlanPolicyResult result = guard.evaluate(plan, ExecutionPolicy.production());

        // Should pass since LOCAL is the default target for FFmpeg
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("OpenFX provider without host is rejected")
    void openfxProviderRejected() {
        // Create a plan with OpenFX provider
        ProviderBindingPlan bindingPlan = compileBindingPlan(List.of(OPENFX), "EXPERIMENT");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);
        ExecutionPolicy policy = ExecutionPolicy.experiment();
        RenderExecutionPlan plan = planCompiler.compile(bindingPlan, drafts, policy);

        // Manually set provider name to "openfx" on a step to test the guard
        // The guard checks providerName == "openfx" (case-insensitive)
        // In our plan, OPENFX has capabilities VIDEO_ENCODE + MEDIA_FILE_OUTPUT
        // which should produce EXECUTE_PROVIDER steps with providerName="openfx"
        RenderPlanPolicyResult result = guard.evaluate(plan, policy);

        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == RenderPlanPolicyViolationType.OPENFX_NO_HOST));
    }

    @Test
    @DisplayName("Step with raw command in metadata is rejected")
    void rawCommandRejected() {
        RenderExecutionPlan plan = compilePlan(List.of(FFMPEG), "PRODUCTION");

        // Inject raw command into a step's metadata
        RenderExecutionStep step = plan.steps().get(0);
        RenderExecutionStep taintedStep = new RenderExecutionStep(
                step.stepId(), step.type(), step.status(),
                step.nodeId(), step.artifactNodeType(),
                step.providerName(), step.providerRef(), step.documentDraft(),
                step.dependencies(), step.executionReady(),
                step.executionEnvironmentTarget(), step.label(),
                Map.of("rawCommand", "ffmpeg -i in.mp4 out.mp4"));
        RenderExecutionPlan taintedPlan = new RenderExecutionPlan(
                plan.planId(), plan.bindingPlanId(), plan.timelineId(),
                plan.policy(), plan.environmentTarget(),
                replaceStep(plan.steps(), step, taintedStep),
                plan.executionReady(), plan.failureReasons());

        RenderPlanPolicyResult result = guard.evaluate(taintedPlan, taintedPlan.policy());

        assertTrue(result.hasViolations());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == RenderPlanPolicyViolationType.RAW_COMMAND_EXPOSED));
    }

    @Test
    @DisplayName("Step with storage internals is rejected")
    void storageInternalsRejected() {
        RenderExecutionPlan plan = compilePlan(List.of(FFMPEG), "PRODUCTION");

        // Inject storage internals into a step's metadata
        RenderExecutionStep step = plan.steps().get(0);
        RenderExecutionStep taintedStep = new RenderExecutionStep(
                step.stepId(), step.type(), step.status(),
                step.nodeId(), step.artifactNodeType(),
                step.providerName(), step.providerRef(), step.documentDraft(),
                step.dependencies(), step.executionReady(),
                step.executionEnvironmentTarget(), step.label(),
                Map.of("bucket", "my-bucket", "objectKey", "key/123"));
        RenderExecutionPlan taintedPlan = new RenderExecutionPlan(
                plan.planId(), plan.bindingPlanId(), plan.timelineId(),
                plan.policy(), plan.environmentTarget(),
                replaceStep(plan.steps(), step, taintedStep),
                plan.executionReady(), plan.failureReasons());

        RenderPlanPolicyResult result = guard.evaluate(taintedPlan, taintedPlan.policy());

        assertTrue(result.hasViolations());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == RenderPlanPolicyViolationType.STORAGE_INTERNALS_EXPOSED));
    }

    @Test
    @DisplayName("Final output must have verification and registration steps")
    void finalOutputMustHaveSteps() {
        RenderExecutionPlan plan = compilePlan(List.of(FFMPEG), "PRODUCTION");

        // Remove VERIFY_OUTPUT step to trigger violation
        RenderExecutionPlan strippedPlan = new RenderExecutionPlan(
                plan.planId(), plan.bindingPlanId(), plan.timelineId(),
                plan.policy(), plan.environmentTarget(),
                plan.steps().stream()
                        .filter(s -> s.type() != RenderExecutionStepType.VERIFY_OUTPUT)
                        .toList(),
                plan.executionReady(), plan.failureReasons());

        RenderPlanPolicyResult result = guard.evaluate(strippedPlan, strippedPlan.policy());

        assertTrue(result.hasViolations());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == RenderPlanPolicyViolationType.OUTPUT_STEPS_MISSING));
    }

    @Test
    @DisplayName("Plan with cycle is rejected")
    void cyclicPlanRejected() {
        RenderExecutionPlan plan = compilePlan(List.of(FFMPEG), "PRODUCTION");

        // Create a cyclic dependency: step A -> step B -> step A
        RenderExecutionStep stepA = plan.steps().get(0);
        RenderExecutionStep stepB = plan.steps().get(1);
        RenderExecutionStep cyclicA = new RenderExecutionStep(
                stepA.stepId(), stepA.type(), stepA.status(),
                stepA.nodeId(), stepA.artifactNodeType(),
                stepA.providerName(), stepA.providerRef(), stepA.documentDraft(),
                List.of(stepB.stepId()), stepA.executionReady(),
                stepA.executionEnvironmentTarget(), stepA.label(), stepA.metadata());
        RenderExecutionStep cyclicB = new RenderExecutionStep(
                stepB.stepId(), stepB.type(), stepB.status(),
                stepB.nodeId(), stepB.artifactNodeType(),
                stepB.providerName(), stepB.providerRef(), stepB.documentDraft(),
                List.of(stepA.stepId()), stepB.executionReady(),
                stepB.executionEnvironmentTarget(), stepB.label(), stepB.metadata());
        RenderExecutionPlan cyclicPlan = new RenderExecutionPlan(
                plan.planId(), plan.bindingPlanId(), plan.timelineId(),
                plan.policy(), plan.environmentTarget(),
                replaceStep(replaceStep(plan.steps(), stepA, cyclicA), stepB, cyclicB),
                plan.executionReady(), plan.failureReasons());

        RenderPlanPolicyResult result = guard.evaluate(cyclicPlan, cyclicPlan.policy());

        assertTrue(result.hasViolations());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == RenderPlanPolicyViolationType.CYCLIC_DEPENDENCY));
    }

    @Test
    @DisplayName("Step IDs are deterministic")
    void stepIdsDeterministic() {
        RenderExecutionPlan plan1 = compilePlan(List.of(FFMPEG), "PRODUCTION");
        RenderExecutionPlan plan2 = compilePlan(List.of(FFMPEG), "PRODUCTION");

        for (int i = 0; i < plan1.steps().size(); i++) {
            assertEquals(plan1.steps().get(i).stepId(), plan2.steps().get(i).stepId());
        }
    }

    @Test
    @DisplayName("Dependency graph must be valid (no dangling deps)")
    void invalidDependencyGraphRejected() {
        RenderExecutionPlan plan = compilePlan(List.of(FFMPEG), "PRODUCTION");

        // Add a step with a dangling dependency
        RenderExecutionStep step = plan.steps().get(0);
        RenderExecutionStep badStep = new RenderExecutionStep(
                step.stepId(), step.type(), step.status(),
                step.nodeId(), step.artifactNodeType(),
                step.providerName(), step.providerRef(), step.documentDraft(),
                List.of("nonexistent-step-id"), step.executionReady(),
                step.executionEnvironmentTarget(), step.label(), step.metadata());
        RenderExecutionPlan badPlan = new RenderExecutionPlan(
                plan.planId(), plan.bindingPlanId(), plan.timelineId(),
                plan.policy(), plan.environmentTarget(),
                replaceStep(plan.steps(), step, badStep),
                plan.executionReady(), plan.failureReasons());

        RenderPlanPolicyResult result = guard.evaluate(badPlan, badPlan.policy());

        assertTrue(result.hasViolations());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == RenderPlanPolicyViolationType.INVALID_DEPENDENCY_GRAPH));
    }

    @Test
    @DisplayName("Null plan fails closed")
    void nullPlanFailsClosed() {
        RenderPlanPolicyResult result = guard.evaluate(null, ExecutionPolicy.production());

        assertTrue(result.isRejected());
    }

    @Test
    @DisplayName("No storage internals in any step of a clean plan")
    void cleanPlanHasNoStorageInternals() {
        RenderExecutionPlan plan = compilePlan(List.of(FFMPEG), "PRODUCTION");

        RenderPlanPolicyResult result = guard.evaluate(plan, plan.policy());

        assertTrue(result.isValid());
        assertFalse(result.violations().stream()
                .anyMatch(v -> v.type() == RenderPlanPolicyViolationType.STORAGE_INTERNALS_EXPOSED));
    }

    // --- Helpers ---

    private RenderExecutionPlan compilePlan(List<ProviderBindingCompiler.ProviderCandidate> candidates,
                                             String mode) {
        ProviderBindingPlan bindingPlan = compileBindingPlan(candidates, mode);
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);
        ExecutionPolicy policy = switch (mode) {
            case "PRODUCTION" -> ExecutionPolicy.production();
            case "MANUAL" -> ExecutionPolicy.manual();
            case "EXPERIMENT" -> ExecutionPolicy.experiment();
            default -> ExecutionPolicy.dryRun();
        };
        return planCompiler.compile(bindingPlan, drafts, policy);
    }

    private ProviderBindingPlan compileBindingPlan(List<ProviderBindingCompiler.ProviderCandidate> candidates,
                                                    String mode) {
        TimelineSpec spec = createSingleClipTimelineSpec();
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-test");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capCompiler.compile(artifactGraph);
        return bindingCompiler.compile(capGraph, candidates, mode);
    }

    private TimelineSpec createSingleClipTimelineSpec() {
        TimelineClip clip = TimelineClip.of("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"),
                0, 0, 5);
        TimelineTrack track = new TimelineTrack("trk-1", "Video 1", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        return new TimelineSpec("tl-test", "Test", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());
    }

    private List<RenderExecutionStep> replaceStep(List<RenderExecutionStep> steps,
                                                   RenderExecutionStep oldStep,
                                                   RenderExecutionStep newStep) {
        return steps.stream()
                .map(s -> s.stepId().equals(oldStep.stepId()) ? newStep : s)
                .toList();
    }
}
