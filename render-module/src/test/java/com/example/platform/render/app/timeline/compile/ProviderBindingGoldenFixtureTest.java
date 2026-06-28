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
 * Golden fixture tests for the binding + execution draft compile pipeline:
 * LogicalCapabilityGraph → ProviderBindingPlan → ProviderExecutionDocumentDrafts.
 *
 * <p>Proves:
 * <ul>
 *   <li>Full pipeline is deterministic for the same inputs</li>
 *   <li>Single video clip → all bound to ffmpeg, all FFMPEG_COMMAND_PLAN drafts</li>
 *   <li>Single clip + caption → subtitle burn-in bound to ffmpeg</li>
 *   <li>No provider candidates → all nodes fail closed</li>
 *   <li>Mixed production + poc → only production selected in PRODUCTION mode</li>
 *   <li>MANUAL mode → poc providers become eligible</li>
 *   <li>Plan summary includes expected counts</li>
 *   <li>Draft IDs are deterministic and unique</li>
 * </ul>
 */
class ProviderBindingGoldenFixtureTest {

    private TimelineNormalizationService normalizer;
    private ArtifactGraphCompiler artifactCompiler;
    private CapabilityGraphCompiler capabilityCompiler;
    private ProviderBindingCompiler bindingCompiler;
    private ProviderExecutionDocumentDraftCompiler draftCompiler;

    private static final List<ProviderBindingCompiler.ProviderCandidate> FULL_PROVIDER_SET = List.of(
            new ProviderBindingCompiler.ProviderCandidate(
                    "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0",
                    true, true, "6.1",
                    List.of("MEDIA_INPUT", "VIDEO_DECODE", "VIDEO_TRIM",
                            "AUDIO_DECODE", "AUDIO_MIX",
                            "VIDEO_ENCODE", "AUDIO_ENCODE", "CONTAINER_MUX",
                            "SUBTITLE_BURN_IN", "FONT_RESOLUTION", "MEDIA_FILE_OUTPUT"),
                    List.of()),
            new ProviderBindingCompiler.ProviderCandidate(
                    "mlt", ProviderStatus.POC, ProviderType.RENDER, "P1",
                    true, true, "7.22",
                    List.of("MEDIA_INPUT", "VIDEO_DECODE", "VIDEO_TRIM", "AUDIO_DECODE",
                            "AUDIO_MIX", "VIDEO_ENCODE", "AUDIO_ENCODE",
                            "CONTAINER_MUX", "MEDIA_FILE_OUTPUT"),
                    List.of()),
            new ProviderBindingCompiler.ProviderCandidate(
                    "blender", ProviderStatus.STUB, ProviderType.RENDER, "P2",
                    false, false, null,
                    List.of("VIDEO_ENCODE", "VIDEO_DECODE", "MEDIA_FILE_OUTPUT"),
                    List.of()));

    @BeforeEach
    void setUp() {
        normalizer = new TimelineNormalizationService();
        artifactCompiler = new ArtifactGraphCompiler();
        capabilityCompiler = new CapabilityGraphCompiler();
        bindingCompiler = new ProviderBindingCompiler();
        draftCompiler = new ProviderExecutionDocumentDraftCompiler();
    }

    @Test
    @DisplayName("Golden: single video clip → full binding pipeline to ffmpeg")
    void goldenSingleVideoClipFullBinding() {
        TimelineSpec spec = createSingleClipSpec();

        // Full pipeline
        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, FULL_PROVIDER_SET, "PRODUCTION");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        // Verify binding plan
        assertNotNull(plan.planId());
        assertTrue(plan.allBound());
        assertFalse(plan.hasFailures());
        assertEquals("PRODUCTION", plan.bindingMode());
        assertEquals(capGraph.nodes().size(), plan.nodes().size());
        assertEquals(capGraph.edges().size(), plan.edges().size());

        // All bound to ffmpeg
        plan.nodes().stream()
                .filter(n -> !n.requiredCapabilities().isEmpty())
                .forEach(n -> assertEquals("ffmpeg", n.boundProviderName()));

        // Verify drafts
        assertFalse(drafts.isEmpty());
        long boundWithReqs = plan.nodes().stream()
                .filter(n -> n.isBound() && !n.requiredCapabilities().isEmpty())
                .count();
        assertEquals(boundWithReqs, drafts.size());

        // All drafts are FFMPEG_COMMAND_PLAN
        drafts.forEach(d -> {
            assertEquals("ffmpeg", d.providerName());
            assertEquals(ProviderExecutionDocumentDraftType.FFMPEG_COMMAND_PLAN, d.documentType());
            assertFalse(d.isReadyForGeneration());
        });

        // Draft IDs are unique
        assertEquals(drafts.size(),
                drafts.stream().map(ProviderExecutionDocumentDraft::draftId).distinct().count());
    }

    @Test
    @DisplayName("Golden: single clip + caption → subtitle burn-in bound to ffmpeg")
    void goldenCaptionOverlayBound() {
        TimelineTextOverlay overlay = TimelineTextOverlay.of("overlay-1", "Hello World", 1.0, 3.0);
        TimelineSpec spec = new TimelineSpec("tl-golden-cap", "Test", null,
                List.of(createSingleTrack()), List.of(overlay),
                TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, FULL_PROVIDER_SET, "PRODUCTION");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        assertTrue(plan.allBound());

        // SUBTITLE_BURN_IN node bound to ffmpeg
        assertTrue(plan.nodes().stream()
                .anyMatch(n -> n.requiredCapabilities().contains("SUBTITLE_BURN_IN")
                        && "ffmpeg".equals(n.boundProviderName())));

        // FFMPEG_COMMAND_PLAN draft exists for subtitle node
        assertTrue(drafts.stream()
                .anyMatch(d -> d.documentType() == ProviderExecutionDocumentDraftType.FFMPEG_COMMAND_PLAN));
    }

    @Test
    @DisplayName("Golden: no candidates → all fail closed, no drafts")
    void goldenNoCandidatesFailClosed() {
        TimelineSpec spec = createSingleClipSpec();

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, List.of(), "PRODUCTION");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(plan);

        assertFalse(plan.allBound());
        assertTrue(plan.hasFailures());
        assertTrue(drafts.isEmpty());

        plan.nodes().stream()
                .filter(n -> !n.requiredCapabilities().isEmpty())
                .forEach(n -> {
                    assertTrue(n.isFailed());
                    assertNotNull(n.decision().failureReason());
                });
    }

    @Test
    @DisplayName("Golden: PRODUCTION mode selects ffmpeg over mlt poc")
    void goldenProductionPrefersProduction() {
        TimelineSpec spec = createSingleClipSpec();

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, FULL_PROVIDER_SET, "PRODUCTION");

        assertTrue(plan.allBound());
        plan.nodes().stream()
                .filter(n -> !n.requiredCapabilities().isEmpty())
                .forEach(n -> assertEquals("ffmpeg", n.boundProviderName(),
                        "PRODUCTION should select ffmpeg over POC mlt"));
    }

    @Test
    @DisplayName("Golden: MANUAL mode makes mlt poc eligible")
    void goldenManualMakesPocEligible() {
        // Only provide mlt (no ffmpeg) so it must be selected
        List<ProviderBindingCompiler.ProviderCandidate> mltOnly = List.of(
                new ProviderBindingCompiler.ProviderCandidate(
                        "mlt", ProviderStatus.POC, ProviderType.RENDER, "P1",
                        true, true, "7.22",
                        List.of("MEDIA_INPUT", "VIDEO_DECODE", "VIDEO_TRIM", "AUDIO_DECODE",
                                "AUDIO_MIX", "VIDEO_ENCODE", "AUDIO_ENCODE",
                                "CONTAINER_MUX", "MEDIA_FILE_OUTPUT"),
                        List.of()));

        TimelineSpec spec = createSingleClipSpec();

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);

        // PRODUCTION mode → fail (poc not production-eligible)
        ProviderBindingPlan prodPlan = bindingCompiler.compile(capGraph, mltOnly, "PRODUCTION");
        assertFalse(prodPlan.allBound());

        // MANUAL mode → success
        ProviderBindingPlan manualPlan = bindingCompiler.compile(capGraph, mltOnly, "MANUAL");
        assertTrue(manualPlan.allBound());
        manualPlan.nodes().stream()
                .filter(n -> !n.requiredCapabilities().isEmpty())
                .forEach(n -> assertEquals("mlt", n.boundProviderName()));
    }

    @Test
    @DisplayName("Golden: plan summary contains expected counts")
    void goldenPlanSummaryContainsCounts() {
        TimelineSpec spec = createSingleClipSpec();

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
        ProviderBindingPlan plan = bindingCompiler.compile(capGraph, FULL_PROVIDER_SET, "PRODUCTION");

        String summary = plan.summary();
        assertTrue(summary.contains("PRODUCTION"));
        assertTrue(summary.contains("allBound=true"));
    }

    @Test
    @DisplayName("Golden: binding plan is deterministic")
    void goldenBindingPlanDeterministic() {
        TimelineSpec spec = createSingleClipSpec();

        NormalizedTimeline timeline = normalizer.normalize(spec, "prj-golden");
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);

        ProviderBindingPlan plan1 = bindingCompiler.compile(capGraph, FULL_PROVIDER_SET, "PRODUCTION");
        ProviderBindingPlan plan2 = bindingCompiler.compile(capGraph, FULL_PROVIDER_SET, "PRODUCTION");

        assertEquals(plan1.planId(), plan2.planId());
        assertEquals(plan1.nodes().size(), plan2.nodes().size());
        for (int i = 0; i < plan1.nodes().size(); i++) {
            assertEquals(plan1.nodes().get(i).nodeId(), plan2.nodes().get(i).nodeId());
            assertEquals(plan1.nodes().get(i).decision().status(), plan2.nodes().get(i).decision().status());
            if (plan1.nodes().get(i).isBound() && plan1.nodes().get(i).decision().selectedProvider() != null) {
                assertEquals(
                        plan1.nodes().get(i).decision().selectedProvider().providerName(),
                        plan2.nodes().get(i).decision().selectedProvider().providerName());
            }
        }
    }

    // --- Helpers ---

    private TimelineSpec createSingleClipSpec() {
        return new TimelineSpec("tl-golden", "Golden Test", "Golden fixture",
                List.of(createSingleTrack()), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0,
                Map.of());
    }

    private TimelineTrack createSingleTrack() {
        TimelineClip clip = TimelineClip.of("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"),
                0, 0, 5);
        return new TimelineTrack("trk-1", "Video 1", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
    }
}
