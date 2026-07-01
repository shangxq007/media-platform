package com.example.platform.render.integration;

import com.example.platform.render.app.timeline.compile.*;
import com.example.platform.render.domain.caption.*;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VS.0 vertical slice integration test.
 *
 * <p>Proves the complete compile pipeline works end-to-end:
 * <ol>
 *   <li>Timeline edit (CaptionTemplateRenderRequest / TimelineSpec)</li>
 *   <li>Caption template (CaptionTemplateTimelineAdapter → TimelineSpec with text overlays)</li>
 *   <li>Timeline normalization (NormalizedTimeline with caption layers)</li>
 *   <li>Artifact dependency graph (with SUBTITLE_OVERLAY node)</li>
 *   <li>Logical capability graph (with SUBTITLE_BURN_IN capability)</li>
 *   <li>Provider binding (deterministic eligibility + priority, FFmpeg-only)</li>
 *   <li>Execution plan (FFmpeg/libass steps, no Remotion dispatch)</li>
 *   <li>Policy guard validation (safety, acyclicity, no internals exposure)</li>
 * </ol>
 *
 * <p>Does NOT execute FFmpeg. Does NOT call StorageRuntime or ProductRuntime.
 * Does NOT dispatch Remotion. Does NOT expose raw commands.</p>
 *
 * <p>Integration package: com.example.platform.render.integration</p>
 */
class VS0VerticalSliceIntegrationTest {

    private TimelineNormalizationService normalizer;
    private ArtifactGraphCompiler artifactCompiler;
    private CapabilityGraphCompiler capabilityCompiler;
    private ProviderBindingCompiler bindingCompiler;
    private ProviderExecutionDocumentDraftCompiler draftCompiler;
    private RenderExecutionPlanCompiler planCompiler;
    private RenderPlanPolicyGuard policyGuard;
    private CaptionTemplateTimelineAdapter captionAdapter;
    private CaptionTemplateRenderContractValidator captionValidator;

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
        normalizer = new TimelineNormalizationService();
        artifactCompiler = new ArtifactGraphCompiler();
        capabilityCompiler = new CapabilityGraphCompiler();
        bindingCompiler = new ProviderBindingCompiler();
        draftCompiler = new ProviderExecutionDocumentDraftCompiler();
        planCompiler = new RenderExecutionPlanCompiler();
        policyGuard = new RenderPlanPolicyGuard();
        captionAdapter = new CaptionTemplateTimelineAdapter();
        captionValidator = new CaptionTemplateRenderContractValidator();
    }

    // ========== VS.0-A: Caption template → TimelineSpec → Compile pipeline ==========

    @Nested
    @DisplayName("VS.0-A: Caption template to TimelineSpec adaptation")
    class CaptionTemplateToTimeline {

        @Test
        @DisplayName("CaptionTemplateRenderRequest adapts to valid TimelineSpec")
        void captionRequestAdaptsToTimelineSpec() {
            CaptionTemplateRenderRequest request = captionRequest();
            CaptionTemplateValidationResult validation = captionValidator.validate(request);
            assertTrue(validation.valid(), "Validation errors: " + validation.errors());

            TimelineSpec spec = captionAdapter.adapt(request);

            assertNotNull(spec, "TimelineSpec must not be null");
            assertNotNull(spec.id(), "Timeline ID must be set");
            assertFalse(spec.tracks().isEmpty(), "Must have at least one track");
            assertFalse(spec.textOverlays().isEmpty(), "Must have text overlays from captions");
            assertEquals(2, spec.textOverlays().size(), "Should have 2 text overlays");
            assertEquals("Hello World", spec.textOverlays().get(0).text());
            assertEquals("VS.0 Integration", spec.textOverlays().get(1).text());
        }

        @Test
        @DisplayName("Caption style preserved through adaptation")
        void captionStylePreserved() {
            CaptionStyleSpec style = new CaptionStyleSpec(
                    CaptionPlacement.TOP_CENTER,
                    new FontStyleSpec("Liberation Sans", 700, "#FFFF00", "#000000", 3, null),
                    36, 3, 1.2, "center");
            CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                    "proj-vs0", "prod-source-vs0",
                    List.of(new CaptionSegmentSpec(0, 3000, "Styled")),
                    new CaptionTemplateSpec(null, "bold-yellow", style),
                    CaptionOutputProfileSpec.hd1080p(), Map.of());

            TimelineSpec spec = captionAdapter.adapt(request);

            assertEquals("Liberation Sans", spec.textOverlays().get(0).fontFamily());
            assertEquals(36, spec.textOverlays().get(0).fontSize());
            assertEquals("#FFFF00", spec.textOverlays().get(0).color());
            assertEquals("top", spec.textOverlays().get(0).positionY());
        }
    }

    // ========== VS.0-B: TimelineSpec → NormalizedTimeline ==========

    @Nested
    @DisplayName("VS.0-B: Timeline normalization preserves captions")
    class TimelineNormalization {

        @Test
        @DisplayName("NormalizedTimeline has caption layers from text overlays")
        void normalizedTimelineHasCaptionLayers() {
            TimelineSpec spec = captionAdapter.adapt(captionRequest());
            NormalizedTimeline timeline = normalizer.normalize(spec, "proj-vs0");

            assertNotNull(timeline, "NormalizedTimeline must not be null");
            assertTrue(timeline.hasCaptions(), "NormalizedTimeline must have captions");
            assertEquals(2, timeline.captionLayers().size(), "Should have 2 caption layers");
            assertEquals("Hello World", timeline.captionLayers().get(0).text());
            assertEquals("VS.0 Integration", timeline.captionLayers().get(1).text());
            assertNotNull(timeline.tracks(), "Must have tracks");
            assertFalse(timeline.tracks().isEmpty(), "Must have at least one track");
        }

        @Test
        @DisplayName("NormalizedTimeline is deterministic")
        void normalizationIsDeterministic() {
            TimelineSpec spec = captionAdapter.adapt(captionRequest());
            NormalizedTimeline t1 = normalizer.normalize(spec, "proj-vs0");
            NormalizedTimeline t2 = normalizer.normalize(spec, "proj-vs0");

            assertEquals(t1.timelineId(), t2.timelineId());
            assertEquals(t1.tracks().size(), t2.tracks().size());
            assertEquals(t1.captionLayers().size(), t2.captionLayers().size());
            assertEquals(t1.outputProfile().format(), t2.outputProfile().format());
        }
    }

    // ========== VS.0-C: NormalizedTimeline → ArtifactDependencyGraph ==========

    @Nested
    @DisplayName("VS.0-C: Artifact graph includes subtitle overlay node")
    class ArtifactGraphCompilation {

        @Test
        @DisplayName("Artifact graph has SUBTITLE_OVERLAY node when captions present")
        void artifactGraphHasSubtitleOverlay() {
            TimelineSpec spec = captionAdapter.adapt(captionRequest());
            NormalizedTimeline timeline = normalizer.normalize(spec, "proj-vs0");
            ArtifactDependencyGraph graph = artifactCompiler.compile(timeline);

            assertNotNull(graph, "Artifact graph must not be null");
            assertNotNull(graph.graphId(), "Graph ID must be set");
            assertFalse(graph.nodes().isEmpty(), "Must have nodes");

            boolean hasSubtitleNode = graph.nodes().stream()
                    .anyMatch(n -> n.type() == ArtifactNodeType.SUBTITLE_OVERLAY);
            assertTrue(hasSubtitleNode, "Artifact graph must have SUBTITLE_OVERLAY node");

            boolean hasInputNode = graph.nodes().stream()
                    .anyMatch(n -> n.type() == ArtifactNodeType.INPUT_MEDIA);
            assertTrue(hasInputNode, "Artifact graph must have INPUT_MEDIA node");

            boolean hasFinalEncode = graph.nodes().stream()
                    .anyMatch(n -> n.type() == ArtifactNodeType.FINAL_ENCODE);
            assertTrue(hasFinalEncode, "Artifact graph must have FINAL_ENCODE node");

            boolean hasFinalRender = graph.nodes().stream()
                    .anyMatch(n -> n.type() == ArtifactNodeType.FINAL_RENDER);
            assertTrue(hasFinalRender, "Artifact graph must have FINAL_RENDER node");
        }

        @Test
        @DisplayName("Artifact graph edges form valid dependency chain")
        void artifactGraphEdgesValid() {
            TimelineSpec spec = captionAdapter.adapt(captionRequest());
            NormalizedTimeline timeline = normalizer.normalize(spec, "proj-vs0");
            ArtifactDependencyGraph graph = artifactCompiler.compile(timeline);

            assertFalse(graph.edges().isEmpty(), "Must have edges");
            // Verify all edges reference valid nodes
            var nodeIds = graph.nodes().stream()
                    .map(ArtifactNode::nodeId)
                    .collect(java.util.stream.Collectors.toSet());
            graph.edges().forEach(edge -> {
                assertTrue(nodeIds.contains(edge.sourceNodeId()),
                        "Edge source must be valid node: " + edge.sourceNodeId());
                assertTrue(nodeIds.contains(edge.targetNodeId()),
                        "Edge target must be valid node: " + edge.targetNodeId());
            });
        }
    }

    // ========== VS.0-D: ArtifactDependencyGraph → LogicalCapabilityGraph ==========

    @Nested
    @DisplayName("VS.0-D: Capability graph maps subtitle to SUBTITLE_BURN_IN")
    class CapabilityGraphCompilation {

        @Test
        @DisplayName("Capability graph has SUBTITLE_BURN_IN capability for subtitle overlay")
        void capabilityGraphHasSubtitleBurnIn() {
            TimelineSpec spec = captionAdapter.adapt(captionRequest());
            NormalizedTimeline timeline = normalizer.normalize(spec, "proj-vs0");
            ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
            LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);

            assertNotNull(capGraph, "Capability graph must not be null");
            assertNotNull(capGraph.graphId(), "Graph ID must be set");

            boolean hasSubtitleCapability = capGraph.nodes().stream()
                    .filter(n -> n.requirement() != null)
                    .flatMap(n -> n.requirement().requiredCapabilities().stream())
                    .anyMatch(cap -> cap.equals("SUBTITLE_BURN_IN"));
            assertTrue(hasSubtitleCapability,
                    "Capability graph must have SUBTITLE_BURN_IN requirement");

            boolean hasMediaInput = capGraph.nodes().stream()
                    .filter(n -> n.requirement() != null)
                    .flatMap(n -> n.requirement().requiredCapabilities().stream())
                    .anyMatch(cap -> cap.equals("MEDIA_INPUT"));
            assertTrue(hasMediaInput, "Capability graph must have MEDIA_INPUT requirement");
        }
    }

    // ========== VS.0-E: Provider binding (deterministic eligibility + priority) ==========

    @Nested
    @DisplayName("VS.0-E: Provider binding is deterministic with FFmpeg-only")
    class ProviderBinding {

        @Test
        @DisplayName("All capability nodes bound to ffmpeg in PRODUCTION mode")
        void allNodesBoundToFfmpeg() {
            ProviderBindingPlan plan = compileBindingPlan();

            assertNotNull(plan, "Binding plan must not be null");
            assertTrue(plan.allBound(), "All nodes must be bound");
            assertFalse(plan.hasFailures(), "Must not have failures");
            assertEquals("PRODUCTION", plan.bindingMode());

            // All nodes with required capabilities should be bound to ffmpeg
            plan.nodes().stream()
                    .filter(n -> !n.requiredCapabilities().isEmpty())
                    .forEach(n -> {
                        assertTrue(n.isBound(), "Node " + n.nodeId() + " must be bound");
                        assertNotNull(n.decision(), "Node must have a decision");
                        assertNotNull(n.decision().selectedProvider(),
                                "Node must have a selected provider");
                        assertEquals("ffmpeg", n.decision().selectedProvider().providerName(),
                                "Node must be bound to ffmpeg");
                    });
        }

        @Test
        @DisplayName("Provider binding plan is deterministic for same input")
        void bindingPlanIsDeterministic() {
            // Use fixed TimelineSpec (not adapter which generates random UUIDs)
            ProviderBindingPlan plan1 = compileBindingPlanFromSpec(captionTimelineSpec());
            ProviderBindingPlan plan2 = compileBindingPlanFromSpec(captionTimelineSpec());

            assertEquals(plan1.planId(), plan2.planId(),
                    "Plan IDs must be deterministic");
            assertEquals(plan1.nodes().size(), plan2.nodes().size(),
                    "Node count must be deterministic");
            assertEquals(plan1.bindingMode(), plan2.bindingMode());
        }

        @Test
        @DisplayName("Final render node identifiable in binding plan")
        void finalRenderNodeIdentifiable() {
            ProviderBindingPlan plan = compileBindingPlan();
            ProviderBindingNode finalRender = plan.finalRenderNode();

            assertNotNull(finalRender, "Final render node must exist");
            assertEquals(ArtifactNodeType.FINAL_RENDER, finalRender.artifactNodeType());
        }

        @Test
        @DisplayName("Provider status is PRODUCTION (not POC/SPIKE)")
        void providerStatusIsProduction() {
            ProviderBindingPlan plan = compileBindingPlan();

            plan.boundNodes().forEach(n -> {
                assertEquals(ProviderStatus.PRODUCTION,
                        n.decision().selectedProvider().providerStatus(),
                        "Bound provider must be PRODUCTION status: " + n.nodeId());
            });
        }
    }

    // ========== VS.0-F: Render execution plan (FFmpeg/libass only, no Remotion) ==========

    @Nested
    @DisplayName("VS.0-F: Render execution plan has FFmpeg steps, no Remotion")
    class ExecutionPlan {

        @Test
        @DisplayName("Execution plan compiles with all expected step types")
        void executionPlanHasExpectedStepTypes() {
            RenderExecutionPlan plan = compileExecutionPlan();

            assertNotNull(plan, "Execution plan must not be null");
            assertNotNull(plan.planId(), "Plan ID must be set");
            assertFalse(plan.steps().isEmpty(), "Must have steps");
            assertEquals(ExecutionEnvironmentTarget.LOCAL, plan.environmentTarget());

            // Verify expected step types
            assertTrue(plan.steps().stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.MATERIALIZE_INPUT),
                    "Must have MATERIALIZE_INPUT");
            assertTrue(plan.steps().stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.PREPARE_PROVIDER_DOCUMENT),
                    "Must have PREPARE_PROVIDER_DOCUMENT");
            assertTrue(plan.steps().stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.EXECUTE_PROVIDER),
                    "Must have EXECUTE_PROVIDER");
            assertTrue(plan.steps().stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.VERIFY_OUTPUT),
                    "Must have VERIFY_OUTPUT");
            assertTrue(plan.steps().stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.REGISTER_OUTPUT),
                    "Must have REGISTER_OUTPUT");
            assertTrue(plan.steps().stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.LINK_PRODUCT_DEPENDENCY),
                    "Must have LINK_PRODUCT_DEPENDENCY");
            assertTrue(plan.steps().stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.FINALIZE_RENDER),
                    "Must have FINALIZE_RENDER");
        }

        @Test
        @DisplayName("All EXECUTE_PROVIDER steps target ffmpeg only")
        void onlyFfmpegProviderSteps() {
            RenderExecutionPlan plan = compileExecutionPlan();

            List<RenderExecutionStep> providerSteps = plan.providerExecutionSteps();
            assertFalse(providerSteps.isEmpty(), "Must have provider execution steps");

            providerSteps.forEach(step -> {
                assertEquals("ffmpeg", step.providerName(),
                        "EXECUTE_PROVIDER must target ffmpeg: " + step.stepId());
            });
        }

        @Test
        @DisplayName("No Remotion references in execution plan")
        void noRemotionInPlan() {
            RenderExecutionPlan plan = compileExecutionPlan();

            plan.steps().forEach(step -> {
                if (step.providerName() != null) {
                    assertNotEquals("remotion", step.providerName().toLowerCase(),
                            "No Remotion provider in plan: " + step.stepId());
                }
                if (step.metadata() != null) {
                    assertFalse(step.metadata().containsKey("remotionComposition"),
                            "No Remotion composition in metadata: " + step.stepId());
                }
            });
        }

        @Test
        @DisplayName("Plan is acyclic")
        void planIsAcyclic() {
            RenderExecutionPlan plan = compileExecutionPlan();

            java.util.Set<String> visited = new java.util.HashSet<>();
            java.util.Set<String> inStack = new java.util.HashSet<>();
            for (RenderExecutionStep step : plan.steps()) {
                assertFalse(hasCycle(step.stepId(), plan, visited, inStack),
                        "Cycle detected involving step: " + step.stepId());
            }
        }

        @Test
        @DisplayName("Step IDs are deterministic for same input")
        void stepIdsDeterministic() {
            RenderExecutionPlan plan1 = compileExecutionPlanFromSpec(captionTimelineSpec());
            RenderExecutionPlan plan2 = compileExecutionPlanFromSpec(captionTimelineSpec());

            assertEquals(plan1.steps().size(), plan2.steps().size());
            for (int i = 0; i < plan1.steps().size(); i++) {
                assertEquals(plan1.steps().get(i).stepId(), plan2.steps().get(i).stepId());
            }
        }

        @Test
        @DisplayName("No raw commands or storage internals in plan steps")
        void noRawCommandsOrStorageInternals() {
            RenderExecutionPlan plan = compileExecutionPlan();

            plan.steps().forEach(step -> {
                if (step.metadata() != null) {
                    assertFalse(step.metadata().containsKey("rawCommand"),
                            "No raw command: " + step.stepId());
                    assertFalse(step.metadata().containsKey("processEnvironment"),
                            "No process environment: " + step.stepId());
                    assertFalse(step.metadata().containsKey("bucket"),
                            "No bucket: " + step.stepId());
                    assertFalse(step.metadata().containsKey("objectKey"),
                            "No objectKey: " + step.stepId());
                    assertFalse(step.metadata().containsKey("signedUrl"),
                            "No signedUrl: " + step.stepId());
                    assertFalse(step.metadata().containsKey("materializedPath"),
                            "No materializedPath: " + step.stepId());
                }
            });
        }

        @Test
        @DisplayName("Final output steps include product output chain")
        void finalOutputIncludesProductChain() {
            RenderExecutionPlan plan = compileExecutionPlan();

            List<RenderExecutionStep> finalOutput = plan.finalOutputSteps();
            assertFalse(finalOutput.isEmpty(), "Must have final output steps");

            assertTrue(finalOutput.stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.VERIFY_OUTPUT),
                    "Final output must have VERIFY_OUTPUT");
            assertTrue(finalOutput.stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.REGISTER_OUTPUT),
                    "Final output must have REGISTER_OUTPUT");
            assertTrue(finalOutput.stream()
                    .anyMatch(s -> s.type() == RenderExecutionStepType.LINK_PRODUCT_DEPENDENCY),
                    "Final output must have LINK_PRODUCT_DEPENDENCY");
        }
    }

    // ========== VS.0-G: Policy guard validates plan safety ==========

    @Nested
    @DisplayName("VS.0-G: Policy guard validates plan safety")
    class PolicyGuardValidation {

        @Test
        @DisplayName("Execution plan passes policy guard")
        void planPassesPolicyGuard() {
            RenderExecutionPlan plan = compileExecutionPlan();
            RenderPlanPolicyResult result = policyGuard.evaluate(plan, plan.policy());

            assertNotNull(result, "Policy result must not be null");
            assertTrue(result.isValid(),
                    "Plan must be valid for dry-run, got: " + result.status());
            assertTrue(result.violations().isEmpty(),
                    "Plan must have no violations: " + result.violations());
        }

        @Test
        @DisplayName("Policy guard detects unbound nodes")
        void guardDetectsUnboundNodes() {
            // Compile with no candidates → all nodes unbound
            TimelineSpec spec = captionAdapter.adapt(captionRequest());
            NormalizedTimeline timeline = normalizer.normalize(spec, "proj-vs0");
            ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
            LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
            ProviderBindingPlan bindingPlan = bindingCompiler.compile(capGraph, List.of(), "PRODUCTION");
            List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);
            RenderExecutionPlan plan = planCompiler.compile(bindingPlan, drafts, ExecutionPolicy.production());

            assertFalse(plan.failureReasons().isEmpty(),
                    "Plan with no candidates must have failure reasons");
            assertTrue(plan.failureReasons().contains(RenderExecutionPlanFailureReason.UNBOUND_CAPABILITY_NODE),
                    "Must detect unbound capability nodes");
        }
    }

    // ========== VS.0-H: Full vertical slice end-to-end ==========

    @Nested
    @DisplayName("VS.0-H: Full vertical slice end-to-end")
    class FullVerticalSlice {

        @Test
        @DisplayName("Full pipeline: Caption request → Timeline → Artifact → Capability → Binding → Plan")
        void fullPipelineFromCaptionRequestToExecutionPlan() {
            // Step 1: Start with caption template render request
            CaptionTemplateRenderRequest request = captionRequest();
            CaptionTemplateValidationResult validation = captionValidator.validate(request);
            assertTrue(validation.valid(), "Request must be valid");

            // Step 2: Adapt to TimelineSpec
            TimelineSpec spec = captionAdapter.adapt(request);
            assertNotNull(spec);
            assertTrue(spec.textOverlays().size() >= 2, "Must have text overlays");

            // Step 3: Normalize
            NormalizedTimeline timeline = normalizer.normalize(spec, "proj-vs0");
            assertTrue(timeline.hasCaptions(), "Must have captions");

            // Step 4: Compile artifact graph
            ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
            assertTrue(artifactGraph.nodes().stream()
                    .anyMatch(n -> n.type() == ArtifactNodeType.SUBTITLE_OVERLAY),
                    "Must have SUBTITLE_OVERLAY node");

            // Step 5: Compile capability graph
            LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
            assertTrue(capGraph.nodes().stream()
                    .filter(n -> n.requirement() != null)
                    .flatMap(n -> n.requirement().requiredCapabilities().stream())
                    .anyMatch("SUBTITLE_BURN_IN"::equals),
                    "Must have SUBTITLE_BURN_IN capability");

            // Step 6: Provider binding
            ProviderBindingPlan bindingPlan = bindingCompiler.compile(
                    capGraph, List.of(FFMPEG), "PRODUCTION");
            assertTrue(bindingPlan.allBound(), "All nodes must be bound to ffmpeg");

            // Step 7: Compile execution plan
            List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);
            RenderExecutionPlan executionPlan = planCompiler.compile(
                    bindingPlan, drafts, ExecutionPolicy.production());

            assertNotNull(executionPlan);
            assertFalse(executionPlan.steps().isEmpty());
            assertEquals(ExecutionEnvironmentTarget.LOCAL, executionPlan.environmentTarget());

            // Verify ffmpeg-only provider
            executionPlan.providerExecutionSteps().forEach(step ->
                    assertEquals("ffmpeg", step.providerName()));

            // Step 8: Policy guard
            RenderPlanPolicyResult policyResult = policyGuard.evaluate(
                    executionPlan, executionPlan.policy());
            assertTrue(policyResult.violations().isEmpty(),
                    "No policy violations expected: " + policyResult.violations());
        }

        @Test
        @DisplayName("Full pipeline: Simple video without captions")
        void fullPipelineSimpleVideo() {
            TimelineSpec spec = simpleVideoTimeline();
            NormalizedTimeline timeline = normalizer.normalize(spec, "proj-simple");

            assertFalse(timeline.hasCaptions(), "Simple video has no captions");

            ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
            assertFalse(artifactGraph.nodes().stream()
                    .anyMatch(n -> n.type() == ArtifactNodeType.SUBTITLE_OVERLAY),
                    "Simple video must not have SUBTITLE_OVERLAY");

            LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
            ProviderBindingPlan bindingPlan = bindingCompiler.compile(
                    capGraph, List.of(FFMPEG), "PRODUCTION");
            assertTrue(bindingPlan.allBound());

            List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);
            RenderExecutionPlan plan = planCompiler.compile(
                    bindingPlan, drafts, ExecutionPolicy.production());

            assertNotNull(plan);
            assertFalse(plan.steps().isEmpty());

            RenderPlanPolicyResult policyResult = policyGuard.evaluate(plan, plan.policy());
            assertTrue(policyResult.violations().isEmpty(),
                    "No policy violations: " + policyResult.violations());
        }

        @Test
        @DisplayName("Plan summary is safe for logging")
        void planSummaryIsSafeForLogging() {
            RenderExecutionPlan plan = compileExecutionPlan();
            RenderExecutionPlanSummary summary = plan.summary();

            assertNotNull(summary);
            assertNotNull(summary.planId());
            assertNotNull(summary.bindingPlanId());
            assertNotNull(summary.timelineId());
            assertTrue(summary.boundProviders().contains("ffmpeg"));
            assertFalse(summary.boundProviders().contains("remotion"));
        }
    }

    // ========== Helpers ==========

    private ProviderBindingPlan compileBindingPlan() {
        TimelineSpec spec = captionAdapter.adapt(captionRequest());
        NormalizedTimeline timeline = normalizer.normalize(spec, "proj-vs0");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
        return bindingCompiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");
    }

    private RenderExecutionPlan compileExecutionPlan() {
        ProviderBindingPlan bindingPlan = compileBindingPlan();
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);
        return planCompiler.compile(bindingPlan, drafts, ExecutionPolicy.production());
    }

    private ProviderBindingPlan compileBindingPlanFromSpec(TimelineSpec spec) {
        NormalizedTimeline timeline = normalizer.normalize(spec, spec.metadata().getOrDefault("projectId", "proj-vs0"));
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
        return bindingCompiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");
    }

    private RenderExecutionPlan compileExecutionPlanFromSpec(TimelineSpec spec) {
        ProviderBindingPlan bindingPlan = compileBindingPlanFromSpec(spec);
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);
        return planCompiler.compile(bindingPlan, drafts, ExecutionPolicy.production());
    }

    private CaptionTemplateRenderRequest captionRequest() {
        return new CaptionTemplateRenderRequest(
                "proj-vs0", "prod-source-vs0",
                List.of(new CaptionSegmentSpec(0, 3000, "Hello World"),
                        new CaptionSegmentSpec(3000, 6000, "VS.0 Integration")),
                null, CaptionOutputProfileSpec.hd1080p(), Map.of());
    }

    private TimelineSpec simpleVideoTimeline() {
        TimelineAssetRef assetRef = TimelineAssetRef.of("asset-simple", "asset://simple-video");
        TimelineClip clip = TimelineClip.of("clip-simple", assetRef, 0, 0, 5);
        TimelineTrack track = new TimelineTrack("trk-v", "Video", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        return new TimelineSpec("tl-simple", "Simple Video", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());
    }

    private TimelineSpec captionTimelineSpec() {
        TimelineAssetRef assetRef = TimelineAssetRef.of("asset-vs0", "asset://vs0-video");
        TimelineClip clip = TimelineClip.of("clip-vs0", assetRef, 0, 0, 6);
        TimelineTrack track = new TimelineTrack("trk-vs0-v", "Video", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineTextOverlay overlay1 = new TimelineTextOverlay(
                "ov-1", "Hello World", "DejaVu Sans", 24,
                "#FFFFFF", "center", "bottom", 0.0, 3.0, null);
        TimelineTextOverlay overlay2 = new TimelineTextOverlay(
                "ov-2", "VS.0 Integration", "DejaVu Sans", 24,
                "#FFFFFF", "center", "bottom", 3.0, 3.0, null);
        return new TimelineSpec("tl-vs0", "VS.0 Test", null,
                List.of(track), List.of(overlay1, overlay2),
                TimelineOutputSpec.mp4_1080p30(), 6.0, Map.of("projectId", "proj-vs0"));
    }

    private boolean hasCycle(String stepId, RenderExecutionPlan plan,
                             java.util.Set<String> visited, java.util.Set<String> inStack) {
        if (inStack.contains(stepId)) return true;
        if (visited.contains(stepId)) return false;
        visited.add(stepId);
        inStack.add(stepId);
        RenderExecutionStep step = plan.findStep(stepId);
        if (step != null && step.dependencies() != null) {
            for (String dep : step.dependencies()) {
                if (hasCycle(dep, plan, visited, inStack)) return true;
            }
        }
        inStack.remove(stepId);
        return false;
    }
}
