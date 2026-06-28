package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProviderBindingCompiler}.
 *
 * <p>Proves:
 * <ul>
 *   <li>Single clip → all nodes bound to ffmpeg in PRODUCTION mode</li>
 *   <li>Caption overlay → subtitle capability bound</li>
 *   <li>No candidates → all nodes fail with REQUIRED_CAPABILITY_MISSING</li>
 *   <li>POC provider rejected in PRODUCTION mode</li>
 *   <li>POC provider accepted in MANUAL mode</li>
 *   <li>STUB provider never eligible</li>
 *   <li>PRODUCTION provider preferred over POC in MANUAL mode</li>
 *   <li>Deterministic plan IDs</li>
 *   <li>Edges mirror capability graph topology</li>
 *   <li>Final render node identifiable in plan</li>
 *   <li>Decision contains candidates for traceability</li>
 *   <li>notFor exclusion prevents binding</li>
 * </ul>
 */
class ProviderBindingCompilerTest {

    private ProviderBindingCompiler compiler;
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

    private static final ProviderBindingCompiler.ProviderCandidate BLENDER_STUB =
            new ProviderBindingCompiler.ProviderCandidate(
                    "blender", ProviderStatus.STUB, ProviderType.RENDER, "P2",
                    false, false, null,
                    List.of("VIDEO_ENCODE", "VIDEO_DECODE", "MEDIA_FILE_OUTPUT"),
                    List.of());

    @BeforeEach
    void setUp() {
        compiler = new ProviderBindingCompiler();
        capCompiler = new CapabilityGraphCompiler();
        artifactCompiler = new ArtifactGraphCompiler();
        normalizer = new TimelineNormalizationService();
    }

    @Test
    @DisplayName("Single clip → all nodes bound to ffmpeg in PRODUCTION mode")
    void singleClipAllBoundToFfmpeg() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan = compiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        assertNotNull(plan);
        assertTrue(plan.allBound());
        assertFalse(plan.hasFailures());
        assertEquals("PRODUCTION", plan.bindingMode());
        assertEquals(capGraph.nodes().size(), plan.nodes().size());

        plan.nodes().stream()
                .filter(n -> !n.requiredCapabilities().isEmpty())
                .forEach(n -> {
                    assertTrue(n.isBound(), "Node " + n.nodeId() + " should be bound");
                    assertEquals("ffmpeg", n.boundProviderName());
                });
    }

    @Test
    @DisplayName("Caption overlay → subtitle burn-in bound")
    void captionOverlayBound() {
        TimelineTextOverlay overlay = TimelineTextOverlay.of("overlay-1", "Hello World", 1.0, 3.0);
        LogicalCapabilityGraph capGraph = compileCapGraphWithOverlays(List.of(overlay));

        ProviderBindingPlan plan = compiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        assertTrue(plan.nodes().stream()
                .anyMatch(n -> n.requiredCapabilities().contains("SUBTITLE_BURN_IN")
                        && n.isBound()));
    }

    @Test
    @DisplayName("No candidates → all capability nodes fail with REQUIRED_CAPABILITY_MISSING")
    void noCandidatesAllFail() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan = compiler.compile(capGraph, List.of(), "PRODUCTION");

        assertFalse(plan.allBound());
        assertTrue(plan.hasFailures());
        plan.nodes().stream()
                .filter(n -> !n.requiredCapabilities().isEmpty())
                .forEach(n -> {
                    assertTrue(n.isFailed());
                    assertEquals(ProviderBindingStatus.UNSUPPORTED, n.decision().status());
                    assertEquals(ProviderBindingFailureReason.REQUIRED_CAPABILITY_MISSING,
                            n.decision().failureReason());
                });
    }

    @Test
    @DisplayName("POC provider rejected in PRODUCTION mode")
    void pocRejectedInProduction() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan = compiler.compile(capGraph, List.of(MLT_POC), "PRODUCTION");

        assertFalse(plan.allBound());
        assertTrue(plan.hasFailures());
    }

    @Test
    @DisplayName("POC provider accepted in MANUAL mode")
    void pocAcceptedInManual() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan = compiler.compile(capGraph, List.of(MLT_POC), "MANUAL");

        assertTrue(plan.allBound(), "POC should be eligible in MANUAL mode");
        assertFalse(plan.hasFailures());
        plan.nodes().stream()
                .filter(n -> !n.requiredCapabilities().isEmpty())
                .forEach(n -> assertEquals("mlt", n.boundProviderName()));
    }

    @Test
    @DisplayName("STUB provider never eligible")
    void stubProviderNeverEligible() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan = compiler.compile(
                capGraph, List.of(BLENDER_STUB), "EXPERIMENT");

        assertFalse(plan.allBound());
    }

    @Test
    @DisplayName("PRODUCTION provider preferred over POC in MANUAL mode")
    void productionPreferredOverPoc() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan = compiler.compile(
                capGraph, List.of(MLT_POC, FFMPEG), "MANUAL");

        assertTrue(plan.allBound());
        plan.nodes().stream()
                .filter(n -> !n.requiredCapabilities().isEmpty())
                .forEach(n -> assertEquals("ffmpeg", n.boundProviderName(),
                        "PRODUCTION provider should be preferred over POC"));
    }

    @Test
    @DisplayName("Deterministic plan IDs")
    void deterministicPlanIds() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan1 = compiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");
        ProviderBindingPlan plan2 = compiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        assertEquals(plan1.planId(), plan2.planId());
    }

    @Test
    @DisplayName("Edges mirror capability graph topology")
    void edgesMirrorTopology() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan = compiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        assertEquals(capGraph.edges().size(), plan.edges().size());
        for (int i = 0; i < capGraph.edges().size(); i++) {
            assertEquals(capGraph.edges().get(i).sourceNodeId(), plan.edges().get(i).sourceNodeId());
            assertEquals(capGraph.edges().get(i).targetNodeId(), plan.edges().get(i).targetNodeId());
            assertEquals(capGraph.edges().get(i).type(), plan.edges().get(i).type());
        }
    }

    @Test
    @DisplayName("Final render node identifiable in plan")
    void finalRenderNodeIdentifiable() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan = compiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        ProviderBindingNode finalRender = plan.finalRenderNode();
        assertNotNull(finalRender);
        assertEquals(ArtifactNodeType.FINAL_RENDER, finalRender.artifactNodeType());
        assertTrue(finalRender.isBound());
    }

    @Test
    @DisplayName("Binding decision contains candidates for traceability")
    void decisionContainsCandidates() {
        LogicalCapabilityGraph capGraph = compileSingleClipCapGraph();

        ProviderBindingPlan plan = compiler.compile(capGraph, List.of(FFMPEG), "PRODUCTION");

        plan.nodes().stream()
                .filter(n -> !n.requiredCapabilities().isEmpty())
                .forEach(n -> {
                    assertNotNull(n.decision().candidates());
                    assertFalse(n.decision().candidates().isEmpty());
                });
    }

    @Test
    @DisplayName("Provider with notFor exclusion → fails for excluded capability")
    void notForExclusion() {
        ProviderBindingCompiler.ProviderCandidate ffmpegExcluded =
                new ProviderBindingCompiler.ProviderCandidate(
                        "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0",
                        true, true, "6.1",
                        List.of("VIDEO_DECODE", "VIDEO_ENCODE", "CONTAINER_MUX"),
                        List.of("SUBTITLE_BURN_IN"));

        TimelineTextOverlay overlay = TimelineTextOverlay.of("overlay-1", "Hello World", 1.0, 3.0);
        LogicalCapabilityGraph capGraph = compileCapGraphWithOverlays(List.of(overlay));

        ProviderBindingPlan plan = compiler.compile(capGraph, List.of(ffmpegExcluded), "PRODUCTION");

        assertTrue(plan.nodes().stream()
                .anyMatch(n -> n.requiredCapabilities().contains("SUBTITLE_BURN_IN")
                        && n.isFailed()));
    }

    // --- Helpers ---

    private LogicalCapabilityGraph compileSingleClipCapGraph() {
        TimelineSpec spec = createSingleClipTimelineSpec();
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-test");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        return capCompiler.compile(artifactGraph);
    }

    private LogicalCapabilityGraph compileCapGraphWithOverlays(List<TimelineTextOverlay> overlays) {
        TimelineSpec spec = createSingleClipTimelineSpec();
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-test");
        // Re-normalize with overlays
        TimelineSpec specWithOverlays = new TimelineSpec(
                spec.id(), spec.name(), spec.description(),
                spec.tracks(), overlays, spec.outputSpec(), spec.totalDuration(), spec.metadata());
        NormalizedTimeline timelineWithOverlays = normalizer.normalize(specWithOverlays, "prj-test");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timelineWithOverlays);
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
