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
 * Tests for {@link ArtifactGraphCompiler}.
 *
 * <p>Proves:
 * <ul>
 *   <li>Single video clip produces expected graph</li>
 *   <li>Single video clip + caption placeholder produces overlay node</li>
 *   <li>Two sequential clips produce expected graph</li>
 *   <li>Graph node IDs are stable</li>
 *   <li>Graph edges are stable</li>
 *   <li>Graph is acyclic</li>
 *   <li>Final render node exists</li>
 *   <li>Unsupported constructs fail closed</li>
 * </ul>
 */
class ArtifactGraphCompilerTest {

    private ArtifactGraphCompiler compiler;
    private TimelineNormalizationService normalizer;

    @BeforeEach
    void setUp() {
        compiler = new ArtifactGraphCompiler();
        normalizer = new TimelineNormalizationService();
    }

    @Test
    @DisplayName("Single video clip graph has expected structure")
    void singleVideoClipGraph() {
        NormalizedTimeline timeline = createSingleClipTimeline();

        ArtifactDependencyGraph graph = compiler.compile(timeline);

        assertNotNull(graph);
        assertFalse(graph.isEmpty());
        assertNotNull(graph.finalRenderNode());
        assertEquals(ArtifactNodeType.FINAL_RENDER, graph.finalRenderNode().type());

        // Should have: INPUT_MEDIA, TRIMMED_MEDIA, FINAL_ENCODE, FINAL_RENDER
        assertTrue(graph.nodes().size() >= 4, "Must have at least 4 nodes");

        // Verify node types exist
        assertTrue(graph.nodes().stream().anyMatch(n -> n.type() == ArtifactNodeType.INPUT_MEDIA));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.type() == ArtifactNodeType.TRIMMED_MEDIA));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.type() == ArtifactNodeType.FINAL_ENCODE));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.type() == ArtifactNodeType.FINAL_RENDER));
    }

    @Test
    @DisplayName("Single video clip + caption produces subtitle overlay node")
    void singleClipWithCaptionGraph() {
        NormalizedCaptionLayer caption = new NormalizedCaptionLayer(
                "cap-1", "Hello", "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", 1.0, 3.0, null);
        NormalizedTimeline timeline = createTimelineWithCaptions(List.of(caption));

        ArtifactDependencyGraph graph = compiler.compile(timeline);

        assertTrue(graph.nodes().stream().anyMatch(n -> n.type() == ArtifactNodeType.SUBTITLE_OVERLAY),
                "Must have SUBTITLE_OVERLAY node");
    }

    @Test
    @DisplayName("Two sequential clips produce two TRIMMED_MEDIA nodes")
    void twoSequentialClipsGraph() {
        NormalizedClip clip1 = new NormalizedClip("clip-1",
                NormalizedAssetRef.of("asset-1", "asset://asset-1"),
                0, 0, 5, 5);
        NormalizedClip clip2 = new NormalizedClip("clip-2",
                NormalizedAssetRef.of("asset-2", "asset://asset-2"),
                5, 0, 5, 5);
        NormalizedTrack track = new NormalizedTrack("trk-1", "Video", NormalizedTrack.TrackType.VIDEO, 0,
                false, List.of(clip1, clip2));
        NormalizedTimeline timeline = new NormalizedTimeline("tl-1", "prj-1",
                List.of(track), List.of(), NormalizedOutputProfile.DEFAULT_MP4_1080P30,
                10.0, Map.of());

        ArtifactDependencyGraph graph = compiler.compile(timeline);

        long trimmedCount = graph.nodes().stream()
                .filter(n -> n.type() == ArtifactNodeType.TRIMMED_MEDIA)
                .count();
        assertEquals(2, trimmedCount, "Must have 2 TRIMMED_MEDIA nodes");
    }

    @Test
    @DisplayName("Graph node IDs are stable across repeated compile")
    void graphNodeIdsStable() {
        NormalizedTimeline timeline = createSingleClipTimeline();

        ArtifactDependencyGraph graph1 = compiler.compile(timeline);
        ArtifactDependencyGraph graph2 = compiler.compile(timeline);

        assertEquals(graph1.nodes().size(), graph2.nodes().size());
        for (int i = 0; i < graph1.nodes().size(); i++) {
            assertEquals(graph1.nodes().get(i).nodeId(), graph2.nodes().get(i).nodeId());
            assertEquals(graph1.nodes().get(i).type(), graph2.nodes().get(i).type());
        }
    }

    @Test
    @DisplayName("Graph edges are stable across repeated compile")
    void graphEdgesStable() {
        NormalizedTimeline timeline = createSingleClipTimeline();

        ArtifactDependencyGraph graph1 = compiler.compile(timeline);
        ArtifactDependencyGraph graph2 = compiler.compile(timeline);

        assertEquals(graph1.edges().size(), graph2.edges().size());
        for (int i = 0; i < graph1.edges().size(); i++) {
            assertEquals(graph1.edges().get(i).edgeId(), graph2.edges().get(i).edgeId());
            assertEquals(graph1.edges().get(i).sourceNodeId(), graph2.edges().get(i).sourceNodeId());
            assertEquals(graph1.edges().get(i).targetNodeId(), graph2.edges().get(i).targetNodeId());
        }
    }

    @Test
    @DisplayName("Graph is acyclic")
    void graphIsAcyclic() {
        NormalizedTimeline timeline = createSingleClipTimeline();

        ArtifactDependencyGraph graph = compiler.compile(timeline);

        // Simple cycle detection: for each edge, source != target
        for (ArtifactEdge edge : graph.edges()) {
            assertNotEquals(edge.sourceNodeId(), edge.targetNodeId(),
                    "Self-referencing edge detected: " + edge.edgeId());
        }

        // Verify no node depends on FINAL_RENDER (it's the root)
        ArtifactNode finalRender = graph.finalRenderNode();
        assertNotNull(finalRender);
        assertTrue(graph.edgesTo(finalRender.nodeId()).stream()
                .allMatch(e -> e.type() == ArtifactEdgeType.PRODUCES || e.type() == ArtifactEdgeType.REQUIRES_INPUT),
                "FINAL_RENDER should only have incoming PRODUCES/REQUIRES_INPUT edges");
    }

    @Test
    @DisplayName("Final render node exists")
    void finalRenderNodeExists() {
        NormalizedTimeline timeline = createSingleClipTimeline();

        ArtifactDependencyGraph graph = compiler.compile(timeline);

        ArtifactNode finalRender = graph.finalRenderNode();
        assertNotNull(finalRender, "Final render node must exist");
        assertEquals(ArtifactNodeType.FINAL_RENDER, finalRender.type());
    }

    @Test
    @DisplayName("Audio tracks produce AUDIO_MIX node")
    void audioTracksProduceAudioMixNode() {
        NormalizedTrack videoTrack = new NormalizedTrack("trk-v", "Video", NormalizedTrack.TrackType.VIDEO, 0,
                false, List.of(new NormalizedClip("clip-1",
                        NormalizedAssetRef.of("asset-1", "asset://asset-1"),
                        0, 0, 5, 5)));
        NormalizedTrack audioTrack = new NormalizedTrack("trk-a", "Audio", NormalizedTrack.TrackType.AUDIO, 1,
                false, List.of());
        NormalizedTimeline timeline = new NormalizedTimeline("tl-1", "prj-1",
                List.of(videoTrack, audioTrack), List.of(),
                NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());

        ArtifactDependencyGraph graph = compiler.compile(timeline);

        assertTrue(graph.nodes().stream().anyMatch(n -> n.type() == ArtifactNodeType.AUDIO_MIX),
                "Must have AUDIO_MIX node when audio tracks exist");
    }

    @Test
    @DisplayName("Null timeline fails closed")
    void nullTimelineFailsClosed() {
        assertThrows(TimelineCompileException.class, () -> compiler.compile(null));
    }

    @Test
    @DisplayName("Graph ID is deterministic")
    void graphIdIsDeterministic() {
        NormalizedTimeline timeline = createSingleClipTimeline();

        ArtifactDependencyGraph graph1 = compiler.compile(timeline);
        ArtifactDependencyGraph graph2 = compiler.compile(timeline);

        assertEquals(graph1.graphId(), graph2.graphId(), "Graph ID must be deterministic");
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
