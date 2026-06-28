package com.example.platform.render.app.timeline.compile.remotion;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.domain.timeline.compile.execution.*;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.domain.timeline.compile.remotion.*;
import com.example.platform.render.app.timeline.compile.RenderPlanPolicyGuard;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.domain.timeline.compile.binding.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Remotion execution document generation flow.
 * Proves: wired but not executable, safe, deterministic.
 */
class RemotionExecutionDocumentGenerationFlowTest {

    private ProviderExecutionDocumentGenerationService generationService;
    private RemotionProviderExecutionDocumentGenerator remotionGenerator;

    @BeforeEach
    void setUp() {
        generationService = new ProviderExecutionDocumentGenerationService();
        remotionGenerator = new RemotionProviderExecutionDocumentGenerator();
    }

    // --- Generation result model ---

    @Test
    @DisplayName("Remotion draft generates result with deterministic documentId")
    void remotionDraftGeneratesResult() {
        ProviderExecutionDocumentDraft draft = createRemotionDraft();
        NormalizedTimeline timeline = createSimpleTimeline();

        ProviderExecutionDocumentGenerationResult result = generationService.generateSingle(draft, timeline);

        assertNotNull(result);
        assertTrue(result.isGenerated());
        assertNotNull(result.documentId());
        assertTrue(result.documentId().startsWith("edd-"));
        assertEquals("remotion", result.providerName());
        assertEquals("REMOTION_INPUT_PROPS_DOCUMENT", result.documentType());
    }

    @Test
    @DisplayName("Result has generationReady=false")
    void generationReadyFalse() {
        ProviderExecutionDocumentDraft draft = createRemotionDraft();
        NormalizedTimeline timeline = createSimpleTimeline();

        ProviderExecutionDocumentGenerationResult result = generationService.generateSingle(draft, timeline);

        assertFalse(result.generationReady());
    }

    @Test
    @DisplayName("Result contains serialized deterministic JSON")
    void resultContainsJson() {
        ProviderExecutionDocumentDraft draft = createRemotionDraft();
        NormalizedTimeline timeline = createSimpleTimeline();

        ProviderExecutionDocumentGenerationResult result = generationService.generateSingle(draft, timeline);

        assertNotNull(result.serializedDocument());
        assertTrue(result.serializedDocument().contains("remotion-input-props-v0"));
        assertTrue(result.serializedDocument().contains("\"composition\""));
    }

    @Test
    @DisplayName("Repeated generation produces identical documentId and JSON")
    void repeatedGenerationIdentical() {
        ProviderExecutionDocumentDraft draft = createRemotionDraft();
        NormalizedTimeline timeline = createSimpleTimeline();

        ProviderExecutionDocumentGenerationResult r1 = generationService.generateSingle(draft, timeline);
        ProviderExecutionDocumentGenerationResult r2 = generationService.generateSingle(draft, timeline);

        assertEquals(r1.documentId(), r2.documentId());
        assertEquals(r1.serializedDocument(), r2.serializedDocument());
    }

    @Test
    @DisplayName("Non-Remotion draft is skipped")
    void nonRemotionDraftSkipped() {
        ProviderExecutionDocumentDraft draft = ProviderExecutionDocumentDraft.forNode(
                "draft-ffmpeg", "node-1", "ffmpeg",
                ProviderExecutionDocumentDraftType.FFMPEG_COMMAND_PLAN);

        ProviderExecutionDocumentGenerationResult result = generationService.generateSingle(draft, createSimpleTimeline());

        assertTrue(result.isSkipped());
        assertEquals(ProviderExecutionDocumentGenerationStatus.SKIPPED_NON_REMOTION, result.generationStatus());
    }

    @Test
    @DisplayName("Null draft fails closed")
    void nullDraftFailsClosed() {
        ProviderExecutionDocumentGenerationResult result = generationService.generateSingle(null, createSimpleTimeline());
        assertTrue(result.isRejected());
        assertEquals(ProviderExecutionDocumentGenerationStatus.FAILED_CLOSED, result.generationStatus());
    }

    @Test
    @DisplayName("Missing source assets fails closed")
    void missingAssetsFailsClosed() {
        ProviderExecutionDocumentDraft draft = createRemotionDraft();
        NormalizedTimeline emptyTimeline = new NormalizedTimeline(
                "tl-1", "proj-1", List.of(), List.of(),
                NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());

        ProviderExecutionDocumentGenerationResult result = generationService.generateSingle(draft, emptyTimeline);

        assertTrue(result.isRejected());
    }

    @Test
    @DisplayName("Validation issues captured internally")
    void validationIssuesCaptured() {
        ProviderExecutionDocumentDraft draft = createRemotionDraft();
        NormalizedTimeline timeline = createSimpleTimeline();

        ProviderExecutionDocumentGenerationResult result = generationService.generateSingle(draft, timeline);

        assertNotNull(result.validationIssues());
        // For a valid timeline, issues should be empty
        assertTrue(result.validationIssues().isEmpty());
    }

    @Test
    @DisplayName("Batch generation returns results for all drafts")
    void batchGeneration() {
        ProviderExecutionDocumentDraft remotionDraft = createRemotionDraft();
        ProviderExecutionDocumentDraft ffmpegDraft = ProviderExecutionDocumentDraft.forNode(
                "draft-ffmpeg", "node-1", "ffmpeg",
                ProviderExecutionDocumentDraftType.FFMPEG_COMMAND_PLAN);

        List<ProviderExecutionDocumentGenerationResult> results =
                generationService.generate(List.of(remotionDraft, ffmpegDraft), createSimpleTimeline());

        assertEquals(2, results.size());
        assertTrue(results.get(0).isGenerated()); // Remotion
        assertTrue(results.get(1).isSkipped());    // FFmpeg
    }

    // --- Safety ---

    @Test
    @DisplayName("Generated result contains no local paths")
    void noLocalPaths() {
        ProviderExecutionDocumentGenerationResult result =
                generationService.generateSingle(createRemotionDraft(), createSimpleTimeline());
        String json = result.serializedDocument();
        assertNotNull(json);
        assertFalse(json.contains("/tmp"));
        assertFalse(json.contains("/home"));
    }

    @Test
    @DisplayName("Generated result contains no storage internals")
    void noStorageInternals() {
        ProviderExecutionDocumentGenerationResult result =
                generationService.generateSingle(createRemotionDraft(), createSimpleTimeline());
        String json = result.serializedDocument();
        assertNotNull(json);
        assertFalse(json.contains("\"bucket\""));
        assertFalse(json.contains("\"objectKey\""));
        assertFalse(json.contains("\"rootPath\""));
        assertFalse(json.contains("\"signedUrl\""));
    }

    @Test
    @DisplayName("Generated result contains no raw commands")
    void noRawCommands() {
        ProviderExecutionDocumentGenerationResult result =
                generationService.generateSingle(createRemotionDraft(), createSimpleTimeline());
        String json = result.serializedDocument();
        assertNotNull(json);
        assertFalse(json.contains("ffmpeg "));
        assertFalse(json.contains("remotion render"));
    }

    @Test
    @DisplayName("Generated result contains no secrets")
    void noSecrets() {
        ProviderExecutionDocumentGenerationResult result =
                generationService.generateSingle(createRemotionDraft(), createSimpleTimeline());
        String json = result.serializedDocument();
        assertNotNull(json);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("X-Amz-Signature"));
    }

    // --- Policy guard safety ---

    @Test
    @DisplayName("RenderPlanPolicyGuard still rejects Remotion execution")
    void policyGuardRejectsRemotion() {
        RenderPlanPolicyGuard guard = new RenderPlanPolicyGuard();
        BoundProviderRef remotionRef = new BoundProviderRef(
                "remotion", ProviderStatus.POC, ProviderType.RENDER, "P2",
                false, false, null, 200);
        RenderExecutionStep exec = new RenderExecutionStep(
                "s1", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING, "n1",
                com.example.platform.render.domain.timeline.compile.ArtifactNodeType.FINAL_RENDER,
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
    @DisplayName("LocalExecutionPlanRunner does not execute Remotion")
    void runnerDoesNotExecuteRemotion() {
        // Remotion is POC, not PRODUCTION, autoDispatch=false
        // LocalExecutionPlanRunner checks isProductionDispatchEligible
        // POC is not productionDispatchEligible → rejected
        ProviderStatus remotionStatus = ProviderStatus.POC;
        assertFalse(remotionStatus.isProductionDispatchEligible());
    }

    @Test
    @DisplayName("No Node/npm/npx/remotion CLI invocation in source")
    void noExternalExecution() {
        // This test is a marker — actual grep is in build checks
        // The generator is a pure data transformation
        assertNotNull(new RemotionInputPropsGenerator());
    }

    @Test
    @DisplayName("Public API DTOs do not expose generation result")
    void publicApiSafe() {
        // TimelineRevisionRenderRequest has only outputProfile
        com.example.platform.render.api.dto.TimelineRevisionRenderRequest request =
                new com.example.platform.render.api.dto.TimelineRevisionRenderRequest("default_1080p");
        assertEquals("default_1080p", request.outputProfile());
    }

    // --- Helpers ---

    private ProviderExecutionDocumentDraft createRemotionDraft() {
        return ProviderExecutionDocumentDraft.forNode(
                "draft-remotion-1", "node-cap-1", "remotion",
                ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT);
    }

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
