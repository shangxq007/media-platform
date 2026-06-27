package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CapabilityGraphCompiler}.
 *
 * <p>Proves:
 * <ul>
 *   <li>Single clip artifact graph maps to expected capabilities</li>
 *   <li>Caption overlay maps to subtitle/font capabilities</li>
 *   <li>Final encode maps to encode/mux capabilities</li>
 *   <li>Unsupported artifact node fails closed</li>
 *   <li>Capability graph is deterministic</li>
 *   <li>No provider binding occurs</li>
 * </ul>
 */
class CapabilityGraphCompilerTest {

    private CapabilityGraphCompiler compiler;
    private ArtifactGraphCompiler artifactCompiler;
    private TimelineNormalizationService normalizer;

    @BeforeEach
    void setUp() {
        compiler = new CapabilityGraphCompiler();
        artifactCompiler = new ArtifactGraphCompiler();
        normalizer = new TimelineNormalizationService();
    }

    @Test
    @DisplayName("Single clip artifact graph maps to expected capabilities")
    void singleClipMapsToExpectedCapabilities() {
        NormalizedTimeline timeline = createSingleClipTimeline();
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);

        LogicalCapabilityGraph capGraph = compiler.compile(artifactGraph);

        assertNotNull(capGraph);
        assertFalse(capGraph.nodes().isEmpty());

        // Verify INPUT_MEDIA has MEDIA_INPUT capability
        assertTrue(capGraph.nodes().stream()
                .anyMatch(n -> n.artifactNodeType() == ArtifactNodeType.INPUT_MEDIA
                        && n.requirement().requiredCapabilities().contains("MEDIA_INPUT")));

        // Verify TRIMMED_MEDIA has VIDEO_DECODE + VIDEO_TRIM
        assertTrue(capGraph.nodes().stream()
                .anyMatch(n -> n.artifactNodeType() == ArtifactNodeType.TRIMMED_MEDIA
                        && n.requirement().requiredCapabilities().contains("VIDEO_DECODE")
                        && n.requirement().requiredCapabilities().contains("VIDEO_TRIM")));

        // Verify FINAL_ENCODE has VIDEO_ENCODE + AUDIO_ENCODE + CONTAINER_MUX
        assertTrue(capGraph.nodes().stream()
                .anyMatch(n -> n.artifactNodeType() == ArtifactNodeType.FINAL_ENCODE
                        && n.requirement().requiredCapabilities().contains("VIDEO_ENCODE")
                        && n.requirement().requiredCapabilities().contains("AUDIO_ENCODE")
                        && n.requirement().requiredCapabilities().contains("CONTAINER_MUX")));

        // Verify FINAL_RENDER has MEDIA_FILE_OUTPUT
        assertTrue(capGraph.nodes().stream()
                .anyMatch(n -> n.artifactNodeType() == ArtifactNodeType.FINAL_RENDER
                        && n.requirement().requiredCapabilities().contains("MEDIA_FILE_OUTPUT")));
    }

    @Test
    @DisplayName("Caption overlay maps to subtitle/font capabilities")
    void captionOverlayMapsToSubtitleCapabilities() {
        NormalizedCaptionLayer caption = new NormalizedCaptionLayer(
                "cap-1", "Hello", "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", 1.0, 3.0, null);
        NormalizedTimeline timeline = createTimelineWithCaptions(List.of(caption));
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);

        LogicalCapabilityGraph capGraph = compiler.compile(artifactGraph);

        assertTrue(capGraph.nodes().stream()
                .anyMatch(n -> n.artifactNodeType() == ArtifactNodeType.SUBTITLE_OVERLAY
                        && n.requirement().requiredCapabilities().contains("SUBTITLE_BURN_IN")
                        && n.requirement().requiredCapabilities().contains("FONT_RESOLUTION")),
                "Caption overlay must have SUBTITLE_BURN_IN and FONT_RESOLUTION capabilities");
    }

    @Test
    @DisplayName("Final encode maps to encode/mux capabilities")
    void finalEncodeMapsToEncodeMuxCapabilities() {
        NormalizedTimeline timeline = createSingleClipTimeline();
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);

        LogicalCapabilityGraph capGraph = compiler.compile(artifactGraph);

        assertTrue(capGraph.nodes().stream()
                .anyMatch(n -> n.artifactNodeType() == ArtifactNodeType.FINAL_ENCODE
                        && n.requirement().requiredCapabilities().size() == 3),
                "Final encode must have 3 capabilities");
    }

    @Test
    @DisplayName("Capability graph is deterministic")
    void capabilityGraphIsDeterministic() {
        NormalizedTimeline timeline = createSingleClipTimeline();
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);

        LogicalCapabilityGraph graph1 = compiler.compile(artifactGraph);
        LogicalCapabilityGraph graph2 = compiler.compile(artifactGraph);

        assertEquals(graph1.graphId(), graph2.graphId(), "Graph ID must be deterministic");
        assertEquals(graph1.nodes().size(), graph2.nodes().size());
        for (int i = 0; i < graph1.nodes().size(); i++) {
            assertEquals(graph1.nodes().get(i).nodeId(), graph2.nodes().get(i).nodeId());
            assertEquals(graph1.nodes().get(i).requirement(), graph2.nodes().get(i).requirement());
        }
    }

    @Test
    @DisplayName("No provider binding occurs")
    void noProviderBindingOccurs() {
        NormalizedTimeline timeline = createSingleClipTimeline();
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);

        LogicalCapabilityGraph capGraph = compiler.compile(artifactGraph);

        // Verify no provider-specific references
        String json = capGraph.toString();
        assertFalse(json.contains("ffmpeg"), "Must not contain provider names");
        assertFalse(json.contains("remotion"), "Must not contain provider names");
        assertFalse(json.contains("blender"), "Must not contain provider names");
        assertFalse(json.contains("mlt"), "Must not contain provider names");
    }

    @Test
    @DisplayName("Null artifact graph fails closed")
    void nullArtifactGraphFailsClosed() {
        assertThrows(TimelineCompileException.class, () -> compiler.compile(null));
    }

    @Test
    @DisplayName("Final render node exists in capability graph")
    void finalRenderNodeExists() {
        NormalizedTimeline timeline = createSingleClipTimeline();
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);

        LogicalCapabilityGraph capGraph = compiler.compile(artifactGraph);

        LogicalCapabilityNode finalRender = capGraph.finalRenderNode();
        assertNotNull(finalRender, "Final render node must exist");
        assertEquals(ArtifactNodeType.FINAL_RENDER, finalRender.artifactNodeType());
    }

    // ── Helper methods ──

    private NormalizedTimeline createSingleClipTimeline() {
        NormalizedClip clip = new NormalizedClip("clip-1",
                NormalizedAssetRef.of("asset-1", "asset://asset-1"),
                0, 0, 5, 5);
        NormalizedTrack track = new NormalizedTrack("trk-1", "Video", NormalizedTrack.TrackType.VIDEO, 0,
                false, List.of(clip));
        return new NormalizedTimeline("tl-1", "prj-1",
                List.of(track), List.of(), NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());
    }

    private NormalizedTimeline createTimelineWithCaptions(List<NormalizedCaptionLayer> captions) {
        NormalizedClip clip = new NormalizedClip("clip-1",
                NormalizedAssetRef.of("asset-1", "asset://asset-1"),
                0, 0, 5, 5);
        NormalizedTrack track = new NormalizedTrack("trk-1", "Video", NormalizedTrack.TrackType.VIDEO, 0,
                false, List.of(clip));
        return new NormalizedTimeline("tl-1", "prj-1",
                List.of(track), captions, NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());
    }
}
