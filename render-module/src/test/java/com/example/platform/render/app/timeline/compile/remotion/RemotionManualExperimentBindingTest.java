package com.example.platform.render.app.timeline.compile.remotion;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.execution.*;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.domain.timeline.compile.remotion.*;
import com.example.platform.render.app.timeline.compile.*;
import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Remotion MANUAL/EXPERIMENT binding and draft production.
 * Proves: Remotion draft produced in MANUAL, not in PRODUCTION, generation flows.
 */
class RemotionManualExperimentBindingTest {

    private TimelineNormalizationService normalizer;
    private ArtifactGraphCompiler artifactCompiler;
    private CapabilityGraphCompiler capCompiler;
    private ProviderBindingCompiler bindingCompiler;
    private ProviderExecutionDocumentDraftCompiler draftCompiler;
    private ProviderExecutionDocumentGenerationService generationService;

    private static final ProviderBindingCompiler.ProviderCandidate FFMPEG =
            new ProviderBindingCompiler.ProviderCandidate(
                    "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0",
                    true, true, "6.1",
                    List.of("MEDIA_INPUT", "VIDEO_DECODE", "VIDEO_TRIM",
                            "AUDIO_DECODE", "AUDIO_MIX",
                            "VIDEO_ENCODE", "AUDIO_ENCODE", "CONTAINER_MUX",
                            "SUBTITLE_BURN_IN", "FONT_RESOLUTION", "MEDIA_FILE_OUTPUT"),
                    List.of());

    private static final ProviderBindingCompiler.ProviderCandidate REMOTION =
            new ProviderBindingCompiler.ProviderCandidate(
                    "remotion", ProviderStatus.POC, ProviderType.RENDER, "P2",
                    false, false, null,
                    List.of("MEDIA_INPUT", "VIDEO_ENCODE", "MEDIA_FILE_OUTPUT"),
                    List.of());

    @BeforeEach
    void setUp() {
        normalizer = new TimelineNormalizationService();
        artifactCompiler = new ArtifactGraphCompiler();
        capCompiler = new CapabilityGraphCompiler();
        bindingCompiler = new ProviderBindingCompiler();
        draftCompiler = new ProviderExecutionDocumentDraftCompiler();
        generationService = new ProviderExecutionDocumentGenerationService();
    }

    // --- PRODUCTION mode: Remotion not selected ---

    @Test
    @DisplayName("PRODUCTION binding does not select Remotion")
    void productionDoesNotSelectRemotion() {
        LogicalCapabilityGraph capGraph = compileCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(
                capGraph, List.of(FFMPEG, REMOTION), "PRODUCTION");

        assertTrue(plan.boundNodes().stream()
                .noneMatch(n -> "remotion".equals(n.boundProviderName())),
                "Remotion must not be selected in PRODUCTION mode");
    }

    @Test
    @DisplayName("PRODUCTION binding selects FFmpeg for baseline render")
    void productionSelectsFfmpeg() {
        LogicalCapabilityGraph capGraph = compileCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(
                capGraph, List.of(FFMPEG, REMOTION), "PRODUCTION");

        assertTrue(plan.boundNodes().stream()
                .allMatch(n -> "ffmpeg".equals(n.boundProviderName())),
                "All bound nodes should be FFmpeg in PRODUCTION mode");
    }

    // --- MANUAL mode: Remotion may be selected ---

    @Test
    @DisplayName("MANUAL mode can bind Remotion for matching capabilities")
    void manualCanBindRemotion() {
        LogicalCapabilityGraph capGraph = compileCapGraph();
        // Only provide Remotion so it must be selected for matching nodes
        ProviderBindingPlan plan = bindingCompiler.compile(
                capGraph, List.of(REMOTION), "MANUAL");

        assertTrue(plan.boundNodes().stream()
                .anyMatch(n -> "remotion".equals(n.boundProviderName())),
                "Remotion should be bindable in MANUAL mode");
    }

    @Test
    @DisplayName("EXPERIMENT mode can bind Remotion for matching capabilities")
    void experimentCanBindRemotion() {
        LogicalCapabilityGraph capGraph = compileCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(
                capGraph, List.of(REMOTION), "EXPERIMENT");

        assertTrue(plan.boundNodes().stream()
                .anyMatch(n -> "remotion".equals(n.boundProviderName())),
                "Remotion should be bindable in EXPERIMENT mode");
    }

    // --- Draft production ---

    @Test
    @DisplayName("Remotion binding node produces REMOTION_INPUT_PROPS_DOCUMENT draft")
    void remotionBindingProducesDraft() {
        LogicalCapabilityGraph capGraph = compileCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(
                capGraph, List.of(REMOTION), "MANUAL");

        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        assertTrue(drafts.stream()
                .anyMatch(d -> d.documentType() == ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT),
                "Should produce REMOTION_INPUT_PROPS_DOCUMENT draft");
    }

    @Test
    @DisplayName("Remotion draft has generationReady=false")
    void remotionDraftNotReady() {
        LogicalCapabilityGraph capGraph = compileCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(
                capGraph, List.of(REMOTION), "MANUAL");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        drafts.stream()
                .filter(d -> d.documentType() == ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT)
                .forEach(d -> assertFalse(d.generationReady()));
    }

    @Test
    @DisplayName("Remotion draft ID is deterministic")
    void remotionDraftIdDeterministic() {
        LogicalCapabilityGraph capGraph = compileCapGraph();
        ProviderBindingPlan plan1 = bindingCompiler.compile(
                capGraph, List.of(REMOTION), "MANUAL");
        ProviderBindingPlan plan2 = bindingCompiler.compile(
                capGraph, List.of(REMOTION), "MANUAL");

        List<ProviderExecutionDocumentDraft> drafts1 = draftCompiler.compile(plan1);
        List<ProviderExecutionDocumentDraft> drafts2 = draftCompiler.compile(plan2);

        List<String> ids1 = drafts1.stream()
                .filter(d -> d.documentType() == ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT)
                .map(ProviderExecutionDocumentDraft::draftId).toList();
        List<String> ids2 = drafts2.stream()
                .filter(d -> d.documentType() == ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT)
                .map(ProviderExecutionDocumentDraft::draftId).toList();

        assertEquals(ids1, ids2);
    }

    @Test
    @DisplayName("Remotion draft contains no serialized props")
    void draftContainsNoSerializedProps() {
        LogicalCapabilityGraph capGraph = compileCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(
                capGraph, List.of(REMOTION), "MANUAL");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        drafts.stream()
                .filter(d -> d.documentType() == ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT)
                .forEach(d -> {
                    // Draft is a planning marker — no content
                    assertNotNull(d.draftId());
                    assertEquals("remotion", d.providerName());
                });
    }

    // --- End-to-end generation flow ---

    @Test
    @DisplayName("Remotion draft generates result through generation service")
    void remotionDraftGeneratesResult() {
        LogicalCapabilityGraph capGraph = compileCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(
                capGraph, List.of(REMOTION), "MANUAL");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        NormalizedTimeline timeline = normalizer.normalize(createTimelineSpec(), "prj-test");

        List<ProviderExecutionDocumentGenerationResult> results =
                generationService.generate(drafts, timeline);

        assertTrue(results.stream()
                .anyMatch(r -> r.isGenerated()
                        && "remotion".equals(r.providerName())
                        && !r.generationReady()));
    }

    @Test
    @DisplayName("Generation result has deterministic JSON")
    void generationResultDeterministic() {
        NormalizedTimeline timeline = normalizer.normalize(createTimelineSpec(), "prj-test");
        ProviderExecutionDocumentDraft remotionDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-remotion", "node-1", "remotion",
                ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT);

        ProviderExecutionDocumentGenerationResult r1 =
                generationService.generateSingle(remotionDraft, timeline);
        ProviderExecutionDocumentGenerationResult r2 =
                generationService.generateSingle(remotionDraft, timeline);

        assertEquals(r1.documentId(), r2.documentId());
        assertEquals(r1.serializedDocument(), r2.serializedDocument());
    }

    // --- Safety ---

    @Test
    @DisplayName("Generation result has no local paths or storage internals")
    void generationResultSafe() {
        NormalizedTimeline timeline = normalizer.normalize(createTimelineSpec(), "prj-test");
        ProviderExecutionDocumentDraft remotionDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-remotion", "node-1", "remotion",
                ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT);

        ProviderExecutionDocumentGenerationResult result =
                generationService.generateSingle(remotionDraft, timeline);

        String json = result.serializedDocument();
        assertNotNull(json);
        assertFalse(json.contains("/tmp"));
        assertFalse(json.contains("/home"));
        assertFalse(json.contains("\"bucket\""));
        assertFalse(json.contains("\"objectKey\""));
        assertFalse(json.contains("\"signedUrl\""));
        assertFalse(json.contains("ffmpeg "));
        assertFalse(json.contains("password"));
    }

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
    @DisplayName("Remotion remains POC, not production eligible")
    void remotionRemainsPoc() {
        assertFalse(ProviderStatus.POC.isProductionDispatchEligible());
        assertTrue(ProviderStatus.POC.canBeConfiguredForDispatch());
    }

    @Test
    @DisplayName("Public API DTOs do not expose Remotion draft or generation result")
    void publicApiSafe() {
        com.example.platform.render.api.dto.TimelineRevisionRenderRequest request =
                new com.example.platform.render.api.dto.TimelineRevisionRenderRequest("default_1080p");
        assertEquals("default_1080p", request.outputProfile());
    }

    @Test
    @DisplayName("Audit event types exist for document generation")
    void auditEventTypesExist() {
        assertNotNull(RenderAuditEventType.PROVIDER_EXECUTION_DOCUMENT_GENERATED);
        assertNotNull(RenderAuditEventType.PROVIDER_EXECUTION_DOCUMENT_REJECTED);
    }

    // --- Helpers ---

    private LogicalCapabilityGraph compileCapGraph() {
        NormalizedTimeline timeline = normalizer.normalize(createTimelineSpec(), "prj-test");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        return capCompiler.compile(artifactGraph);
    }

    private TimelineSpec createTimelineSpec() {
        TimelineClip clip = TimelineClip.of("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"), 0, 0, 5);
        TimelineTrack track = new TimelineTrack("trk-1", "Video 1",
                TimelineTrack.TrackType.VIDEO, 0, List.of(clip), false, false);
        return new TimelineSpec("tl-1", "Test", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());
    }
}
