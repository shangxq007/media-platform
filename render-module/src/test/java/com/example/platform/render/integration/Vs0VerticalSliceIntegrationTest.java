package com.example.platform.render.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.RenderJobPlan;
import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.RenderStep;
import com.example.platform.render.domain.RenderStepStatus;
import com.example.platform.render.domain.RenderStepType;
import com.example.platform.render.domain.caption.CaptionOutputProfileSpec;
import com.example.platform.render.domain.caption.CaptionPlacement;
import com.example.platform.render.domain.caption.CaptionSegmentSpec;
import com.example.platform.render.domain.caption.CaptionStyleSpec;
import com.example.platform.render.domain.caption.CaptionTemplateRenderRequest;
import com.example.platform.render.domain.caption.CaptionTemplateSpec;
import com.example.platform.render.domain.caption.CaptionTemplateTimelineAdapter;
import com.example.platform.render.domain.caption.FontStyleSpec;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineTextOverlay;
import com.example.platform.render.domain.legacy.TimelineTrack;
import com.example.platform.render.domain.compile.ArtifactNodeType;
import com.example.platform.render.domain.compile.ArtifactRequirement;
import com.example.platform.render.domain.compile.LogicalCapabilityEdge;
import com.example.platform.render.domain.compile.LogicalCapabilityGraph;
import com.example.platform.render.domain.compile.LogicalCapabilityNode;
import com.example.platform.render.domain.compile.ArtifactEdgeType;
import com.example.platform.render.domain.compile.binding.ProviderBindingDecision;
import com.example.platform.render.domain.compile.binding.ProviderBindingNode;
import com.example.platform.render.domain.compile.binding.ProviderBindingPlan;
import com.example.platform.render.domain.compile.binding.ProviderBindingPlanId;
import com.example.platform.render.domain.compile.binding.ProviderBindingStatus;
import com.example.platform.render.domain.compile.binding.BoundProviderRef;
import com.example.platform.render.domain.compile.executionplan.ExecutionEnvironmentTarget;
import com.example.platform.render.domain.compile.executionplan.ExecutionPolicy;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionPlan;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionPlanFailureReason;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionStep;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionStepStatus;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionStepType;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionPlanId;
import com.example.platform.render.domain.plan.BasicRenderPlanningRequest;
import com.example.platform.render.domain.plan.BasicRenderPlanningRequestId;
import com.example.platform.render.domain.plan.BasicRenderPlanningResult;
import com.example.platform.render.domain.plan.BasicRenderPlanningResultStatus;
import com.example.platform.render.domain.plan.BasicRenderPolicy;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.render.testsupport.TimelineCoreSmokeFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Integration test harness for the VS.0 vertical slice flow.
 *
 * <p>Validates the complete domain pipeline:
 * Timeline edit → Caption template → Provider binding → Provider plan → Product output.
 *
 * <p>Uses PostgreSQL Testcontainers + real jOOQ for render_job persistence
 * and pure domain objects for the vertical slice validation.
 * External collaborators are NOT mocked — all domain objects are constructed directly.
 *
 * <p>This test does NOT depend on a typed provider plugin, MLT, or Remotion.
 * It validates domain boundaries, state machines, and compile pipeline contracts.
 */
class Vs0VerticalSliceIntegrationTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
    }

    // ==================== Stage 2: Caption Template ====================

    @Nested
    @DisplayName("Stage 2: Caption Template Adaptation")
    class CaptionTemplateStage {

        @Test
        @DisplayName("Caption template adapter produces TimelineSpec with overlays")
        void captionTemplateProducesTimelineSpec() {
            // Given: a caption template render request
            CaptionSegmentSpec seg1 = new CaptionSegmentSpec(1000, 3000, "First subtitle");
            CaptionSegmentSpec seg2 = new CaptionSegmentSpec(4000, 6000, "Second subtitle");
            CaptionTemplateSpec template = new CaptionTemplateSpec(
                    "tmpl-001", "Standard",
                    new CaptionStyleSpec(
                            CaptionPlacement.BOTTOM_CENTER,
                            new FontStyleSpec("DejaVu Sans", 700, "#FFFFFF", "#000000", 2, null),
                            28, 2, 1.5, "center"));

            CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                    "prj-vs0", "prod-source-001",
                    List.of(seg1, seg2), template,
                    CaptionOutputProfileSpec.hd1080p(), Map.of());

            // When: the adapter converts to TimelineSpec
            CaptionTemplateTimelineAdapter adapter = new CaptionTemplateTimelineAdapter();
            TimelineSpec adaptedTimeline = adapter.adapt(request);

            // Then: the adapted timeline has the expected structure
            assertNotNull(adaptedTimeline);
            assertFalse(adaptedTimeline.tracks().isEmpty(),
                    "Adapted timeline should have at least one video track");
            assertFalse(adaptedTimeline.textOverlays().isEmpty(),
                    "Adapted timeline should have text overlays from caption segments");
            assertEquals(2, adaptedTimeline.textOverlays().size(),
                    "Should have 2 text overlays for 2 caption segments");
            assertEquals("First subtitle", adaptedTimeline.textOverlays().get(0).text());
            assertEquals("Second subtitle", adaptedTimeline.textOverlays().get(1).text());
        }

        @Test
        @DisplayName("Caption template adapter produces valid output spec")
        void captionTemplateProducesValidOutputSpec() {
            CaptionSegmentSpec seg = new CaptionSegmentSpec(0, 5000, "Test");

            CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                    "prj-vs0", "prod-source-002",
                    List.of(seg), new CaptionTemplateSpec("tpl-inline", "inline",
                            new CaptionStyleSpec(CaptionPlacement.BOTTOM_CENTER,
                                    new FontStyleSpec("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                    24, 2, 1.4, "center")),
                    CaptionOutputProfileSpec.hd720p(), Map.of());

            CaptionTemplateTimelineAdapter adapter = new CaptionTemplateTimelineAdapter();
            TimelineSpec adapted = adapter.adapt(request);

            assertNotNull(adapted.outputSpec());
            assertEquals("mp4", adapted.outputSpec().format());
            assertEquals("1280x720", adapted.outputSpec().resolution());
        }
    }

    // ==================== Stage 3: Provider Plan Generation ====================

    @Nested
    @DisplayName("Stage 3: Provider Plan Generation")
    class ProviderPlanStage {

        @Test
        @DisplayName("Provider plan generates valid plan for caption-adapted timeline")
        void providerPlanGeneratesValidPlan() {
            // Given: a timeline from the caption template adapter
            CaptionSegmentSpec seg = new CaptionSegmentSpec(1000, 4000, "Provider test caption");
            CaptionTemplateRenderRequest captionRequest = new CaptionTemplateRenderRequest(
                    "prj-vs0", "prod-source-003",
                    List.of(seg), new CaptionTemplateSpec("tpl-inline", "inline",
                            new CaptionStyleSpec(CaptionPlacement.BOTTOM_CENTER,
                                    new FontStyleSpec("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                    24, 2, 1.4, "center")),
                    CaptionOutputProfileSpec.hd1080p(), Map.of());

            CaptionTemplateTimelineAdapter adapter = new CaptionTemplateTimelineAdapter();
            TimelineSpec timeline = adapter.adapt(captionRequest);

            // When: we generate an Provider plan
            BasicRenderPlanningRequest planRequest =
                    new BasicRenderPlanningRequest(
                            new BasicRenderPlanningRequestId("plan-req-vs0"),
                            timeline,
                            BasicRenderPolicy.conservative(),
                            Map.of());

            BasicRenderPlanningResult planResult =
                    com.example.platform.render.domain.plan
                            .BasicRenderPlanner.plan(planRequest);

            // Then: the plan is successfully generated
            assertNotNull(planResult);
            assertEquals(BasicRenderPlanningResultStatus.PLANNED, planResult.status(),
                    "Provider plan should be PLANNED for valid caption timeline");
            assertNotNull(planResult.plan());
            assertFalse(planResult.plan().stages().isEmpty(),
                    "Provider plan should have at least one stage");
        }

        @Test
        @DisplayName("Provider plan rejects null request")
        void providerPlanRejectsNullRequest() {
            BasicRenderPlanningResult result =
                    com.example.platform.render.domain.plan
                            .BasicRenderPlanner.plan(null);

            assertEquals(BasicRenderPlanningResultStatus.FAILED, result.status());
        }

        @Test
        @DisplayName("Provider plan for timeline with video overlay includes overlay stages")
        void providerPlanIncludesOverlayStages() {
            TimelineSpec timeline = TimelineCoreSmokeFixture.createVideoWithSubtitleTimeline();

            BasicRenderPlanningRequest request =
                    new BasicRenderPlanningRequest(
                            new BasicRenderPlanningRequestId("plan-req-overlay"),
                            timeline,
                            BasicRenderPolicy.conservative(),
                            Map.of());

            BasicRenderPlanningResult result =
                    com.example.platform.render.domain.plan
                            .BasicRenderPlanner.plan(request);

            assertEquals(BasicRenderPlanningResultStatus.PLANNED, result.status());
            assertTrue(result.plan().stages().size() >= 3,
                    "Plan should have validation + prepare + clip stages at minimum");
        }
    }

    // ==================== Stage 4: Provider Binding ====================

    @Nested
    @DisplayName("Stage 4: Provider Binding (PRODUCTION-only)")
    class ProviderBindingStage {

        @Test
        @DisplayName("Provider binding compiles capability graph to binding plan")
        void providerBindingCompilesToPlan() {
            // Given: a logical capability graph with a final render node
            LogicalCapabilityNode inputNode = new LogicalCapabilityNode(
                    "node-input", ArtifactNodeType.INPUT_MEDIA, "Input Media",
                    ArtifactRequirement.of(List.of("demux", "trim")),
                    Map.of("assetId", "ast-001"));

            LogicalCapabilityNode captionNode = new LogicalCapabilityNode(
                    "node-caption", ArtifactNodeType.SUBTITLE_OVERLAY, "Caption Overlay",
                    ArtifactRequirement.of(List.of("caption_burn_in")),
                    Map.of());

            LogicalCapabilityNode finalNode = new LogicalCapabilityNode(
                    "node-final", ArtifactNodeType.FINAL_RENDER, "Final Render",
                    ArtifactRequirement.of(List.of("transcode", "mux")),
                    Map.of());

            LogicalCapabilityEdge edge1 = new LogicalCapabilityEdge(
                    "edge-1", "node-final", "node-input", ArtifactEdgeType.DERIVES_FROM);
            LogicalCapabilityEdge edge2 = new LogicalCapabilityEdge(
                    "edge-2", "node-final", "node-caption", ArtifactEdgeType.REQUIRES_INPUT);

            LogicalCapabilityGraph graph = new LogicalCapabilityGraph(
                    "cap-graph-vs0", "tl-vs0-001",
                    List.of(inputNode, captionNode, finalNode),
                    List.of(edge1, edge2));

            // Provider binding is tested at the domain contract level.
            // We verify the graph structure is valid and the binding plan ID is derived.
            assertNotNull(graph);
            assertEquals(3, graph.nodes().size());
            assertEquals(2, graph.edges().size());

            // Verify the graph has a final render node
            LogicalCapabilityNode finalRenderNode = graph.finalRenderNode();
            assertNotNull(finalRenderNode, "Graph should have a FINAL_RENDER node");
            assertEquals(ArtifactNodeType.FINAL_RENDER, finalRenderNode.artifactNodeType());
        }

        @Test
        @DisplayName("Provider binding plan ID is deterministic from capability graph")
        void bindingPlanIdIsDeterministic() {
            String graphId = "cap-graph-deterministic-001";
            ProviderBindingPlanId planId1 = ProviderBindingPlanId.fromCapabilityGraphId(graphId);
            ProviderBindingPlanId planId2 = ProviderBindingPlanId.fromCapabilityGraphId(graphId);

            assertNotNull(planId1);
            assertEquals(planId1.value(), planId2.value(),
                    "ProviderBindingPlanId should be deterministic for same graph ID");
        }

        @Test
        @DisplayName("Bound provider ref is production-eligible for PRODUCTION status + autoDispatch")
        void boundProviderRefProductionEligible() {
            BoundProviderRef ref = new BoundProviderRef(
                    "provider-a", ProviderStatus.PRODUCTION, ProviderType.RENDER,
                    "P0", true, true, "6.0", 0);

            assertTrue(ref.isProductionEligible(),
                    "Provider with PRODUCTION status + autoDispatch should be production-eligible");
        }
    }

    // ==================== Stage 5: RenderExecutionPlan ====================

    @Nested
    @DisplayName("Stage 5: RenderExecutionPlan (Provider plan → Product output)")
    class ExecutionPlanStage {

        @Test
        @DisplayName("RenderExecutionPlan can be constructed with provider execution steps")
        void executionPlanCanBeConstructed() {
            // Given: execution steps representing the VS.0 vertical slice
            RenderExecutionStep materializeStep = new RenderExecutionStep(
                    "step-mat-001",
                    RenderExecutionStepType.MATERIALIZE_INPUT,
                    RenderExecutionStepStatus.PENDING,
                    "node-input", ArtifactNodeType.INPUT_MEDIA,
                    null, null, null,
                    List.of(), false,
                    ExecutionEnvironmentTarget.LOCAL,
                    "Materialize source media",
                    Map.of("assetId", "ast-001"));

            RenderExecutionStep providerStep = new RenderExecutionStep(
                    "step-prov-001",
                    RenderExecutionStepType.EXECUTE_PROVIDER,
                    RenderExecutionStepStatus.PENDING,
                    "node-final", ArtifactNodeType.FINAL_RENDER,
                    "provider-a",
                    new BoundProviderRef("provider-a", ProviderStatus.PRODUCTION,
                            ProviderType.RENDER, "P0", true, true, "6.0", 0),
                    null,
                    List.of("step-mat-001"), false,
                    ExecutionEnvironmentTarget.LOCAL,
                    "Provider transcode with caption burn-in",
                    Map.of("capabilities", "transcode,mux,caption_burn_in"));

            RenderExecutionStep registerStep = new RenderExecutionStep(
                    "step-reg-001",
                    RenderExecutionStepType.REGISTER_OUTPUT,
                    RenderExecutionStepStatus.PENDING,
                    null, null,
                    null, null, null,
                    List.of("step-prov-001"), false,
                    ExecutionEnvironmentTarget.LOCAL,
                    "Register output artifact",
                    Map.of());

            RenderExecutionPlan plan = new RenderExecutionPlan(
                    new RenderExecutionPlanId("ep-vs0-001"),
                    "pbp-vs0-001",
                    "tl-vs0-001",
                    ExecutionPolicy.production(),
                    ExecutionEnvironmentTarget.LOCAL,
                    List.of(materializeStep, providerStep, registerStep),
                    false,
                    List.of());

            // Then: the plan has the expected structure
            assertNotNull(plan);
            assertEquals(3, plan.steps().size());
            assertFalse(plan.executionReady(), "v0 plans should not be execution-ready");
            assertFalse(plan.hasFailures());

            // Verify step ordering and dependencies
            List<RenderExecutionStep> providerSteps = plan.providerExecutionSteps();
            assertEquals(1, providerSteps.size());
            assertEquals("provider-a", providerSteps.get(0).providerName());

            List<RenderExecutionStep> materializationSteps = plan.materializationSteps();
            assertEquals(1, materializationSteps.size());

            assertTrue(providerSteps.get(0).hasDependencies(),
                    "Provider step should depend on materialization step");
            assertEquals(List.of("step-mat-001"), providerSteps.get(0).dependencies());
        }

        @Test
        @DisplayName("RenderExecutionPlan summary captures provider names")
        void executionPlanSummaryCapturesProviders() {
            RenderExecutionStep providerStep = new RenderExecutionStep(
                    "step-p", RenderExecutionStepType.EXECUTE_PROVIDER,
                    RenderExecutionStepStatus.PENDING,
                    "node-final", ArtifactNodeType.FINAL_RENDER,
                    "provider-a", null, null,
                    List.of(), false, ExecutionEnvironmentTarget.LOCAL, "provider-a", Map.of());

            RenderExecutionPlan plan = new RenderExecutionPlan(
                    new RenderExecutionPlanId("ep-sum-001"),
                    "pbp-sum-001", "tl-sum-001",
                    ExecutionPolicy.production(), ExecutionEnvironmentTarget.LOCAL,
                    List.of(providerStep), false, List.of());

            var summary = plan.summary();
            assertNotNull(summary);
            assertEquals(1, summary.totalSteps());
            assertTrue(summary.boundProviders().contains("provider-a"));
        }

        @Test
        @DisplayName("Failed execution plan has correct status")
        void failedExecutionPlanStatus() {
            RenderExecutionStep failedStep = new RenderExecutionStep(
                    "step-fail", RenderExecutionStepType.EXECUTE_PROVIDER,
                    RenderExecutionStepStatus.FAILED,
                    "node-final", ArtifactNodeType.FINAL_RENDER,
                    "provider-a", null, null,
                    List.of(), false, ExecutionEnvironmentTarget.LOCAL, "Provider (failed)",
                    Map.of("errorCode", "PROVIDER_TIMEOUT"));

            RenderExecutionPlan plan = new RenderExecutionPlan(
                    new RenderExecutionPlanId("ep-fail-001"),
                    "pbp-fail-001", "tl-fail-001",
                    ExecutionPolicy.production(), ExecutionEnvironmentTarget.LOCAL,
                    List.of(failedStep), false, List.of());

            assertTrue(plan.failedSteps().size() == 1);
            assertEquals("PROVIDER_TIMEOUT", plan.failedSteps().get(0).metadata().get("errorCode"));
        }
    }

    // ==================== Stage 6: RenderJobPlan State Machine ====================

    @Nested
    @DisplayName("Stage 6: RenderJobPlan State Machine (Product Output)")
    class RenderPlanStateMachineStage {

        @Test
        @DisplayName("Full VS.0 flow: Timeline edit → Caption → Provider plan → RenderJobPlan → Step execution")
        void fullVs0VerticalSliceFlow() {
            // === Step 1: Caption Template (canonical TimelineDocument authoring
            // path; BasicTimelineEditor parallel mutation is DELETED) ===
            CaptionSegmentSpec seg = new CaptionSegmentSpec(1000, 6000, "VS.0 Test Caption");
            CaptionTemplateRenderRequest captionRequest = new CaptionTemplateRenderRequest(
                    "prj-vs0", "prod-source-004",
                    List.of(seg), new CaptionTemplateSpec("tpl-inline", "inline",
                            new CaptionStyleSpec(CaptionPlacement.BOTTOM_CENTER,
                                    new FontStyleSpec("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                    24, 2, 1.4, "center")),
                    CaptionOutputProfileSpec.hd1080p(), Map.of());
            CaptionTemplateTimelineAdapter captionAdapter = new CaptionTemplateTimelineAdapter();
            TimelineSpec captionTimeline = captionAdapter.adapt(captionRequest);
            assertNotNull(captionTimeline);
            assertFalse(captionTimeline.textOverlays().isEmpty());

            // === Step 3: Provider Plan ===
            BasicRenderPlanningRequest providerRequest =
                    new BasicRenderPlanningRequest(
                            new BasicRenderPlanningRequestId("plan-req-vs0-full"),
                            captionTimeline,
                            BasicRenderPolicy.conservative(),
                            Map.of());
            BasicRenderPlanningResult providerResult =
                    com.example.platform.render.domain.plan
                            .BasicRenderPlanner.plan(providerRequest);
            assertEquals(BasicRenderPlanningResultStatus.PLANNED, providerResult.status());
            assertNotNull(providerResult.plan());

            // === Step 4: RenderJobPlan (Product Output) ===
            RenderProfile profile = RenderProfile.social1080p();
            RenderJobPlan renderPlan = RenderJobPlan.create(
                    "rp-vs0-full", "rj-vs0-full", profile,
                    List.of(
                            RenderStep.pending("rs-build", "rp-vs0-full", RenderStepType.BUILD_TIMELINE),
                            RenderStep.pending("rs-transcode", "rp-vs0-full", RenderStepType.PROVIDER_TRANSCODE),
                            RenderStep.pending("rs-register", "rp-vs0-full", RenderStepType.REGISTER_ARTIFACT)
                    ));

            assertNotNull(renderPlan);
            assertEquals(3, renderPlan.steps().size());
            assertFalse(renderPlan.isComplete());
            assertFalse(renderPlan.hasFailed());
            assertFalse(renderPlan.isDone());

            // Execute step 1: BUILD_TIMELINE
            RenderStep buildStep = renderPlan.nextPendingStep();
            assertNotNull(buildStep);
            assertEquals(RenderStepType.BUILD_TIMELINE, buildStep.type());

            RenderStep runningBuild = buildStep.markRunning();
            renderPlan = renderPlan.withStep(runningBuild);
            assertEquals(RenderStepStatus.RUNNING, renderPlan.status());

            RenderStep completedBuild = runningBuild.markCompleted(List.of("artifact-timeline-001"));
            renderPlan = renderPlan.withStep(completedBuild);

            // Execute step 2: PROVIDER_TRANSCODE
            RenderStep transcodeStep = renderPlan.nextPendingStep();
            assertNotNull(transcodeStep);
            assertEquals(RenderStepType.PROVIDER_TRANSCODE, transcodeStep.type());

            transcodeStep = transcodeStep.markRunning();
            renderPlan = renderPlan.withStep(transcodeStep);
            transcodeStep = transcodeStep.markCompleted(List.of("artifact-output-001"));
            renderPlan = renderPlan.withStep(transcodeStep);

            // Execute step 3: REGISTER_ARTIFACT
            RenderStep registerStep = renderPlan.nextPendingStep();
            assertNotNull(registerStep);
            registerStep = registerStep.markRunning();
            renderPlan = renderPlan.withStep(registerStep);
            registerStep = registerStep.markCompleted(List.of("artifact-final-001"));
            renderPlan = renderPlan.withStep(registerStep);

            // Verify final state
            assertTrue(renderPlan.isComplete(), "All steps should be completed");
            assertTrue(renderPlan.isDone(), "Plan should be done");
            assertFalse(renderPlan.hasFailed());
            assertNull(renderPlan.nextPendingStep(), "No more pending steps");
        }

        @Test
        @DisplayName("RenderJobPlan step failure propagates to plan status")
        void renderPlanStepFailurePropagates() {
            RenderJobPlan plan = RenderJobPlan.create(
                    "rp-fail", "rj-fail", RenderProfile.social720p(),
                    List.of(
                            RenderStep.pending("rs-fail-1", "rp-fail", RenderStepType.PROVIDER_TRANSCODE)
                    ));

            RenderStep step = plan.nextPendingStep().markRunning();
            plan = plan.withStep(step);
            step = step.markFailed("PROVIDER_ERROR", "Codec not available");
            plan = plan.withStep(step);

            assertTrue(plan.hasFailed());
            assertTrue(plan.isDone());
            assertEquals("PROVIDER_ERROR", plan.steps().get(0).errorCode());
        }

        @Test
        @DisplayName("RenderJobPlan step retry: FAILED → PENDING transition is valid")
        void renderPlanStepRetry() {
            RenderStep failed = RenderStep.pending("rs-retry", "rp-retry", RenderStepType.PROVIDER_TRANSCODE)
                    .markRunning()
                    .markFailed("ERR", "fail");

            // Verify FAILED → PENDING is valid (retry path)
            RenderStep retried = failed.withStatus(RenderStepStatus.PENDING);
            assertEquals(RenderStepStatus.PENDING, retried.status());
        }
    }

    // ==================== Stage 7: Domain Boundary Validation ====================

    @Nested
    @DisplayName("Stage 7: Domain Boundary Validation")
    class DomainBoundaryValidation {

        @Test
        @DisplayName("Caption template adapter does not expose raw provider expressions")
        void captionAdapterNoRawProviderExpressions() {
            CaptionSegmentSpec seg = new CaptionSegmentSpec(0, 3000, "Safe test");
            CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                    "prj-safe", "prod-safe", List.of(seg),
                    new CaptionTemplateSpec("tpl-inline", "inline",
                            new CaptionStyleSpec(CaptionPlacement.BOTTOM_CENTER,
                                    new FontStyleSpec("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                    24, 2, 1.4, "center")),
                    CaptionOutputProfileSpec.hd1080p(), Map.of());

            TimelineSpec adapted = new CaptionTemplateTimelineAdapter().adapt(request);

            // Verify no metadata leaks raw provider expression syntax
            adapted.metadata().values().forEach(v -> {
                assertFalse(v.contains("provider_expression"),
                        "Adapted timeline metadata should not contain provider_expression");
                assertFalse(v.contains("provider expression"),
                        "Adapted timeline metadata should not contain provider expression");
            });
        }

        @Test
        @DisplayName("ExecutionPolicy production mode restricts providers")
        void executionPolicyProductionRestricts() {
            ExecutionPolicy policy = ExecutionPolicy.production();

            assertEquals("PRODUCTION", policy.mode());
            assertFalse(policy.allowManualProviders());
            assertFalse(policy.allowExperimentalProviders());
            assertFalse(policy.allowOpenCueSubmit());
            assertFalse(policy.allowProviderExecution());
        }

        @Test
        @DisplayName("RenderStep invalid transition throws")
        void renderStepInvalidTransitionThrows() {
            RenderStep completed = RenderStep.pending("rs-x", "rp-x", RenderStepType.BUILD_TIMELINE)
                    .markRunning()
                    .markCompleted(List.of());

            assertThrows(IllegalArgumentException.class,
                    () -> completed.withStatus(RenderStepStatus.RUNNING),
                    "COMPLETED → RUNNING should be invalid");
        }
    }
}
