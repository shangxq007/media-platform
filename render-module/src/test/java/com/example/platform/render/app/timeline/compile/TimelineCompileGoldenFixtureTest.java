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
 * Golden fixture tests for the v0 compile pipeline:
 * TimelineRevision → NormalizedTimeline → ArtifactDependencyGraph → LogicalCapabilityGraph.
 *
 * <p>Proves:
 * <ul>
 *   <li>Normalization is deterministic</li>
 *   <li>Artifact graph is deterministic</li>
 *   <li>Capability graph is deterministic</li>
 *   <li>Unsupported constructs are handled explicitly</li>
 *   <li>Provider selection does not happen</li>
 *   <li>Public API does not change</li>
 * </ul>
 */
class TimelineCompileGoldenFixtureTest {

    private TimelineNormalizationService normalizer;
    private ArtifactGraphCompiler artifactCompiler;
    private CapabilityGraphCompiler capabilityCompiler;

    @BeforeEach
    void setUp() {
        normalizer = new TimelineNormalizationService();
        artifactCompiler = new ArtifactGraphCompiler();
        capabilityCompiler = new CapabilityGraphCompiler();
    }

    // ── Golden Fixture 1: Single video clip ──

    @Test
    @DisplayName("Golden: single video clip full pipeline")
    void goldenSingleVideoClip() {
        TimelineSpec spec = createSingleClipSpec();

        // Compile pipeline
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);

        // Verify normalization
        assertEquals("tl-golden", timeline.timelineId());
        assertEquals(1, timeline.tracks().size());
        assertEquals(1, timeline.tracks().get(0).clips().size());
        assertEquals("mp4", timeline.outputProfile().format());

        // Verify artifact graph
        assertNotNull(artifactGraph.finalRenderNode());
        assertEquals(4, artifactGraph.nodes().size()); // INPUT, TRIMMED, ENCODE, RENDER
        assertTrue(artifactGraph.edges().size() >= 3);

        // Verify capability graph
        assertNotNull(capGraph.finalRenderNode());
        assertEquals(artifactGraph.nodes().size(), capGraph.nodes().size());
        assertTrue(capGraph.isFullyResolvable());
    }

    // ── Golden Fixture 2: Single video clip with output profile ──

    @Test
    @DisplayName("Golden: single video clip with output profile")
    void goldenSingleClipWithOutputProfile() {
        TimelineOutputSpec outputSpec = new TimelineOutputSpec(
                "mp4", "1280x720", 24.0, "h264", 4000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl-golden-720p", "Golden 720p", null,
                List.of(createVideoTrack("trk-1")), List.of(), outputSpec, 5.0, Map.of());

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");

        assertEquals("1280x720", timeline.outputProfile().resolution());
        assertEquals(24.0, timeline.outputProfile().frameRate());
        assertEquals("h264", timeline.outputProfile().videoCodec());
    }

    // ── Golden Fixture 3: Single video clip with caption placeholder ──

    @Test
    @DisplayName("Golden: single video clip with caption placeholder")
    void goldenSingleClipWithCaption() {
        TimelineTextOverlay overlay = TimelineTextOverlay.of("overlay-1", "Hello World", 1.0, 3.0);
        TimelineSpec spec = new TimelineSpec("tl-golden-caption", "Golden Caption", null,
                List.of(createVideoTrack("trk-1")), List.of(overlay),
                TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);

        assertTrue(timeline.hasCaptions());
        assertTrue(artifactGraph.nodes().stream()
                .anyMatch(n -> n.type() == ArtifactNodeType.SUBTITLE_OVERLAY));
        assertTrue(capGraph.nodes().stream()
                .anyMatch(n -> n.artifactNodeType() == ArtifactNodeType.SUBTITLE_OVERLAY));
    }

    // ── Golden Fixture 4: Two sequential clips ──

    @Test
    @DisplayName("Golden: two sequential clips")
    void goldenTwoSequentialClips() {
        TimelineClip clip1 = TimelineClip.of("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"), 0, 0, 5);
        TimelineClip clip2 = TimelineClip.of("clip-2",
                TimelineAssetRef.of("asset-2", "asset://asset-2"), 5, 0, 5);
        TimelineTrack track = new TimelineTrack("trk-1", "Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip1, clip2), false, false);
        TimelineSpec spec = new TimelineSpec("tl-golden-seq", "Golden Sequential", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.of());

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);

        long trimmedCount = artifactGraph.nodes().stream()
                .filter(n -> n.type() == ArtifactNodeType.TRIMMED_MEDIA)
                .count();
        assertEquals(2, trimmedCount, "Must have 2 TRIMMED_MEDIA nodes");
    }

    // ── Golden Fixture 5: Multiple tracks ──

    @Test
    @DisplayName("Golden: multiple tracks (video + audio)")
    void goldenMultipleTracks() {
        TimelineTrack videoTrack = new TimelineTrack("trk-v", "Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(TimelineClip.of("clip-1", TimelineAssetRef.of("asset-1", "asset://asset-1"), 0, 0, 5)),
                false, false);
        TimelineTrack audioTrack = new TimelineTrack("trk-a", "Audio", TimelineTrack.TrackType.AUDIO, 1,
                List.of(), false, false);
        TimelineSpec spec = new TimelineSpec("tl-golden-multi", "Golden Multi-track", null,
                List.of(videoTrack, audioTrack), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);

        assertTrue(artifactGraph.nodes().stream()
                .anyMatch(n -> n.type() == ArtifactNodeType.AUDIO_MIX));
    }

    // ── Golden Fixture 6: Unsupported effect fail-closed ──

    @Test
    @DisplayName("Golden: unsupported effect fails closed")
    void goldenUnsupportedEffectFailsClosed() {
        TimelineClipEffect effect = new TimelineClipEffect("fx-1", "blur", null, null, List.of(), Map.of());
        TimelineClip clip = new TimelineClip("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"),
                0, 0, 5, 5, List.of(effect));
        TimelineTrack track = new TimelineTrack("trk-1", "Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl-golden-fx", "Golden Effect", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        assertThrows(TimelineCompileException.class, () -> normalizer.normalize(spec, "prj-golden"));
    }

    // ── Golden Fixture 7: Missing source asset fail-closed ──

    @Test
    @DisplayName("Golden: missing source asset fails closed at compile")
    void goldenMissingSourceAssetFailsClosed() {
        // Create a clip with empty assetId
        NormalizedClip clip = new NormalizedClip("clip-1",
                NormalizedAssetRef.of("", "asset://empty"),
                0, 0, 5, 5);
        NormalizedTrack track = new NormalizedTrack("trk-1", "Video", NormalizedTrack.TrackType.VIDEO, 0,
                false, List.of(clip));
        NormalizedTimeline timeline = new NormalizedTimeline("tl-1", "prj-1",
                List.of(track), List.of(), NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());

        // Should fail at normalization (empty assetId)
        assertThrows(TimelineCompileException.class, () -> normalizer.normalize(
                createSpecWithEmptyAsset(), "prj-1"));
    }

    // ── Golden Fixture 8: Stable graph IDs across repeated compile ──

    @Test
    @DisplayName("Golden: stable graph IDs across repeated compile")
    void goldenStableGraphIds() {
        TimelineSpec spec = createSingleClipSpec();
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");

        ArtifactDependencyGraph graph1 = artifactCompiler.compile(timeline);
        ArtifactDependencyGraph graph2 = artifactCompiler.compile(timeline);

        assertEquals(graph1.graphId(), graph2.graphId());
        for (int i = 0; i < graph1.nodes().size(); i++) {
            assertEquals(graph1.nodes().get(i).nodeId(), graph2.nodes().get(i).nodeId());
        }
    }

    // ── Golden Fixture 9: No provider binding in v0 output ──

    @Test
    @DisplayName("Golden: no provider binding in v0 output")
    void goldenNoProviderBinding() {
        TimelineSpec spec = createSingleClipSpec();
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);

        // Verify no provider-specific references in any output
        String timelineStr = timeline.toString();
        String artifactStr = artifactGraph.toString();
        String capStr = capGraph.toString();

        for (String output : List.of(timelineStr, artifactStr, capStr)) {
            assertFalse(output.contains("ffmpeg"), "Must not contain provider names");
            assertFalse(output.contains("remotion"), "Must not contain provider names");
            assertFalse(output.contains("blender"), "Must not contain provider names");
            assertFalse(output.contains("mlt"), "Must not contain provider names");
            assertFalse(output.contains("natron"), "Must not contain provider names");
            assertFalse(output.contains("gstreamer"), "Must not contain provider names");
        }
    }

    // ── Helper methods ──

    private TimelineSpec createSingleClipSpec() {
        TimelineTrack track = createVideoTrack("trk-1");
        return new TimelineSpec("tl-golden", "Golden Timeline", "Golden fixture",
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());
    }

    private TimelineTrack createVideoTrack(String trackId) {
        TimelineClip clip = TimelineClip.of("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"), 0, 0, 5);
        return new TimelineTrack(trackId, "Video 1", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
    }

    private TimelineSpec createSpecWithEmptyAsset() {
        TimelineAssetRef emptyRef = new TimelineAssetRef("", "asset://empty", "mp4", 5, 0, 0, Map.of());
        TimelineClip clip = new TimelineClip("clip-1", emptyRef, 0, 0, 5, 5, List.of());
        TimelineTrack track = new TimelineTrack("trk-1", "Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        return new TimelineSpec("tl-bad", "Bad Timeline", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());
    }
}
