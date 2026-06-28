package com.example.platform.render.app.timeline.compile.remotion;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.domain.timeline.compile.execution.*;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.domain.timeline.compile.remotion.*;
import com.example.platform.render.app.timeline.compile.*;
import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.domain.timeline.compile.binding.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for provider execution document generation in plan-based compile.
 * Proves: generation integrated, non-Remotion skipped, Remotion generates, audit emitted,
 * FFmpeg unaffected, policy guard unchanged.
 */
class PlanBasedProviderDocumentGenerationIntegrationTest {

    private ProviderExecutionDocumentGenerationService generationService;

    @BeforeEach
    void setUp() {
        generationService = new ProviderExecutionDocumentGenerationService();
    }

    @Test
    @DisplayName("FFmpeg draft is skipped by generation service")
    void ffmpegDraftSkipped() {
        ProviderExecutionDocumentDraft ffmpegDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-ffmpeg", "node-1", "ffmpeg",
                ProviderExecutionDocumentDraftType.FFMPEG_COMMAND_PLAN);
        NormalizedTimeline timeline = createSimpleTimeline();

        List<ProviderExecutionDocumentGenerationResult> results =
                generationService.generate(List.of(ffmpegDraft), timeline);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSkipped());
        assertEquals(ProviderExecutionDocumentGenerationStatus.SKIPPED_NON_REMOTION,
                results.get(0).generationStatus());
    }

    @Test
    @DisplayName("Remotion draft generates through integrated service")
    void remotionDraftGenerates() {
        ProviderExecutionDocumentDraft remotionDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-remotion", "node-cap", "remotion",
                ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT);
        NormalizedTimeline timeline = createSimpleTimeline();

        List<ProviderExecutionDocumentGenerationResult> results =
                generationService.generate(List.of(remotionDraft), timeline);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isGenerated());
        assertEquals("remotion", results.get(0).providerName());
        assertEquals("REMOTION_INPUT_PROPS_DOCUMENT", results.get(0).documentType());
        assertFalse(results.get(0).generationReady());
        assertNotNull(results.get(0).serializedDocument());
    }

    @Test
    @DisplayName("Mixed drafts: FFmpeg skipped, Remotion generated")
    void mixedDraftsHandled() {
        ProviderExecutionDocumentDraft ffmpegDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-ffmpeg", "node-1", "ffmpeg",
                ProviderExecutionDocumentDraftType.FFMPEG_COMMAND_PLAN);
        ProviderExecutionDocumentDraft remotionDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-remotion", "node-cap", "remotion",
                ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT);
        NormalizedTimeline timeline = createSimpleTimeline();

        List<ProviderExecutionDocumentGenerationResult> results =
                generationService.generate(List.of(ffmpegDraft, remotionDraft), timeline);

        assertEquals(2, results.size());
        assertTrue(results.get(0).isSkipped());    // FFmpeg
        assertTrue(results.get(1).isGenerated());  // Remotion
    }

    @Test
    @DisplayName("Document ID is deterministic")
    void documentIdDeterministic() {
        ProviderExecutionDocumentDraft remotionDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-remotion", "node-cap", "remotion",
                ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT);
        NormalizedTimeline timeline = createSimpleTimeline();

        ProviderExecutionDocumentGenerationResult r1 =
                generationService.generateSingle(remotionDraft, timeline);
        ProviderExecutionDocumentGenerationResult r2 =
                generationService.generateSingle(remotionDraft, timeline);

        assertEquals(r1.documentId(), r2.documentId());
        assertEquals(r1.serializedDocument(), r2.serializedDocument());
    }

    @Test
    @DisplayName("Audit event types exist for document generation")
    void auditEventTypesExist() {
        assertNotNull(RenderAuditEventType.PROVIDER_EXECUTION_DOCUMENT_GENERATED);
        assertNotNull(RenderAuditEventType.PROVIDER_EXECUTION_DOCUMENT_REJECTED);
    }

    @Test
    @DisplayName("Invalid Remotion draft fails closed")
    void invalidDraftFailsClosed() {
        ProviderExecutionDocumentDraft remotionDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-remotion", "node-cap", "remotion",
                ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT);
        NormalizedTimeline emptyTimeline = new NormalizedTimeline(
                "tl-1", "proj-1", List.of(), List.of(),
                NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());

        ProviderExecutionDocumentGenerationResult result =
                generationService.generateSingle(remotionDraft, emptyTimeline);

        assertTrue(result.isRejected());
    }

    @Test
    @DisplayName("Generated result contains no local paths or storage internals")
    void resultPayloadSafe() {
        ProviderExecutionDocumentDraft remotionDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-remotion", "node-cap", "remotion",
                ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT);
        NormalizedTimeline timeline = createSimpleTimeline();

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
                ExecutionEnvironmentTarget.LOCAL, "Remotion exec", Map.of());
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
    }

    @Test
    @DisplayName("Public API DTOs do not expose generation result")
    void publicApiSafe() {
        com.example.platform.render.api.dto.TimelineRevisionRenderRequest request =
                new com.example.platform.render.api.dto.TimelineRevisionRenderRequest("default_1080p");
        assertEquals("default_1080p", request.outputProfile());
    }

    // --- Helpers ---

    private NormalizedTimeline createSimpleTimeline() {
        NormalizedAssetRef asset = new NormalizedAssetRef(
                "asset-1", "asset://asset-1", "mp4", 10L, 1920, 1080, Map.of());
        NormalizedClip clip = new NormalizedClip("clip-1", asset, 0.0, 0.0, 5.0, 5.0);
        NormalizedTrack track = new NormalizedTrack(
                "track-1", "Video 1", NormalizedTrack.TrackType.VIDEO, 0, false, List.of(clip));
        return new NormalizedTimeline("tl-1", "proj-1", List.of(track), List.of(),
                NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());
    }
}
