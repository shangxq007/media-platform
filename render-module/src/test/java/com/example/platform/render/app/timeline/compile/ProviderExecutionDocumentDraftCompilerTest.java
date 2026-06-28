package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.execution.*;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProviderExecutionDocumentDraftCompiler}.
 *
 * <p>Proves:
 * <ul>
 *   <li>FFmpeg bound nodes → FFMPEG_COMMAND_PLAN drafts</li>
 *   <li>MLT bound nodes → MLT_PROJECT_DOCUMENT drafts</li>
 *   <li>Unbound nodes produce no drafts</li>
 *   <li>Mixed bound/unbound → only bound nodes produce drafts</li>
 *   <li>Drafts are never generation-ready (v0)</li>
 *   <li>Deterministic draft IDs</li>
 *   <li>Draft requirement populated with provider name and document type</li>
 *   <li>Null binding plan throws IllegalArgumentException</li>
 * </ul>
 */
class ProviderExecutionDocumentDraftCompilerTest {

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

    private static final ProviderBindingCompiler.ProviderCandidate MLT =
            new ProviderBindingCompiler.ProviderCandidate(
                    "mlt", ProviderStatus.POC, ProviderType.RENDER, "P1",
                    true, true, "7.22",
                    List.of("MEDIA_INPUT", "VIDEO_DECODE", "VIDEO_TRIM", "AUDIO_DECODE",
                            "AUDIO_MIX", "VIDEO_ENCODE", "AUDIO_ENCODE",
                            "CONTAINER_MUX", "MEDIA_FILE_OUTPUT"),
                    List.of());

    @BeforeEach
    void setUp() {
        bindingCompiler = new ProviderBindingCompiler();
        draftCompiler = new ProviderExecutionDocumentDraftCompiler();
        capCompiler = new CapabilityGraphCompiler();
        artifactCompiler = new ArtifactGraphCompiler();
        normalizer = new TimelineNormalizationService();
    }

    @Test
    @DisplayName("FFmpeg bound nodes → FFMPEG_COMMAND_PLAN drafts")
    void ffmpegProducesCommandPlanDrafts() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        assertFalse(drafts.isEmpty());
        assertTrue(plan.allBound());

        long boundWithReqs = plan.nodes().stream()
                .filter(n -> n.isBound() && !n.requiredCapabilities().isEmpty())
                .count();
        assertEquals(boundWithReqs, drafts.size());

        drafts.forEach(d -> {
                    assertEquals("ffmpeg", d.providerName());
                    assertEquals(ProviderExecutionDocumentDraftType.FFMPEG_COMMAND_PLAN,
                            d.documentType());
                    assertFalse(d.isReadyForGeneration());
                    assertNotNull(d.draftId());
                    assertNotNull(d.bindingNodeId());
                });
    }

    @Test
    @DisplayName("MLT bound nodes → MLT_PROJECT_DOCUMENT drafts")
    void mltProducesProjectDocumentDrafts() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, List.of(MLT), "MANUAL");

        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        assertTrue(drafts.stream().allMatch(d ->
                d.documentType() == ProviderExecutionDocumentDraftType.MLT_PROJECT_DOCUMENT));
        assertTrue(drafts.stream().allMatch(d -> "mlt".equals(d.providerName())));
    }

    @Test
    @DisplayName("Unbound nodes produce no drafts")
    void unboundNodesProduceNoDrafts() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, List.of(), "PRODUCTION");

        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        assertTrue(drafts.isEmpty());
    }

    @Test
    @DisplayName("Mixed bound/unbound → only bound nodes produce drafts")
    void mixedBoundUnbound() {
        ProviderBindingCompiler.ProviderCandidate partial =
                new ProviderBindingCompiler.ProviderCandidate(
                        "partial", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0",
                        true, true, "1.0",
                        List.of("MEDIA_INPUT", "VIDEO_DECODE", "VIDEO_TRIM"),
                        List.of());

        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, List.of(partial), "PRODUCTION");

        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        assertTrue(plan.hasFailures());
        assertFalse(drafts.isEmpty());
        drafts.forEach(d -> {
            ProviderBindingNode node = plan.nodes().stream()
                    .filter(n -> n.nodeId().equals(d.bindingNodeId()))
                    .findFirst().orElse(null);
            assertNotNull(node);
            assertTrue(node.isBound());
        });
    }

    @Test
    @DisplayName("Drafts are never generation-ready in v0")
    void draftsNeverGenerationReady() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        drafts.forEach(d -> assertFalse(d.isReadyForGeneration()));
    }

    @Test
    @DisplayName("Deterministic draft IDs")
    void deterministicDraftIds() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        List<ProviderExecutionDocumentDraft> drafts1 = draftCompiler.compile(plan);
        List<ProviderExecutionDocumentDraft> drafts2 = draftCompiler.compile(plan);

        assertEquals(drafts1.size(), drafts2.size());
        for (int i = 0; i < drafts1.size(); i++) {
            assertEquals(drafts1.get(i).draftId(), drafts2.get(i).draftId());
        }
    }

    @Test
    @DisplayName("Draft requirement populated with provider name and document type")
    void draftRequirementPopulated() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        drafts.forEach(d -> {
            assertNotNull(d.requirement());
            assertEquals("ffmpeg", d.requirement().providerName());
            assertEquals(d.documentType(), d.requirement().documentType());
        });
    }

    @Test
    @DisplayName("Null binding plan throws IllegalArgumentException")
    void nullPlanThrows() {
        assertThrows(IllegalArgumentException.class, () -> draftCompiler.compile(null));
    }

    // --- Helpers ---

    private LogicalCapabilityGraph compileSingleClipCapGraph() {
        TimelineSpec spec = createSingleClipTimelineSpec();
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-test");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        return capCompiler.compile(artifactGraph);
    }

    private TimelineSpec createSingleClipTimelineSpec() {
        TimelineClip clip = TimelineClip.of("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"),
                0, 0, 5);
        TimelineTrack track = new TimelineTrack("trk-1", "Video 1",
                TimelineTrack.TrackType.VIDEO, 0, List.of(clip), false, false);
        return new TimelineSpec("tl-1", "Test Timeline", "Test description",
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0,
                Map.of());
    }
}
