package com.example.platform.render.app.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemporalVisibilityAnalyzerTest {

    private static final FrameRate FPS = FrameRate.of(1, 1);
    private final TemporalVisibilityAnalyzer analyzer = new TemporalVisibilityAnalyzer();

    @Test
    void classifiesOutsidePartialAndFullContributions() {
        TimelineDocument document = document(video(
                clip("outside", "art-o", MediaTime.ofRational(1, 2), MediaTime.ofRational(1, 1),
                        MediaTime.ZERO, MediaTime.ofRational(1, 2)),
                clip("full", "art-f", MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1),
                        MediaTime.ZERO, MediaTime.ofRational(1, 1))));

        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ofRational(1, 1), MediaTime.ofRational(3, 1));

        assertThat(analysis.crossTrackPruningEnabled()).isFalse();
        assertThat(analysis.candidates()).isEmpty();
        ClipVisibility outside = analysis.tracks().getFirst().clips().get(0);
        ClipVisibility full = analysis.tracks().getFirst().clips().get(1);
        assertThat(outside.decision()).isEqualTo(VisibilityDecision.OUTSIDE_REQUEST);
        assertThat(outside.contribution()).isEqualTo(VisibilityContribution.NONE);
        assertThat(outside.decodeDependency().required()).isTrue();
        assertThat(full.decision()).isEqualTo(VisibilityDecision.CONTRIBUTES);
        assertThat(full.contribution()).isEqualTo(VisibilityContribution.FULL);
        assertThat(full.sourceWindow()).contains(sourceWindow(MediaTime.ZERO, MediaTime.ofRational(1, 1)));
    }

    @Test
    void trimsPartialIntersectionInBothTimelineAndSourceCoordinates() {
        TimelineDocument document = document(video(
                clip("clip", "art", MediaTime.ZERO, MediaTime.ofRational(2, 1),
                        MediaTime.ofRational(3, 1), MediaTime.ofRational(5, 1))));

        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ofRational(1, 1), MediaTime.ofRational(3, 1));
        ClipVisibility visibility = analysis.tracks().getFirst().clips().getFirst();

        assertThat(visibility.timelineCoverage()).contains(coverage(MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1)));
        assertThat(visibility.sourceWindow()).contains(sourceWindow(MediaTime.ofRational(4, 1), MediaTime.ofRational(5, 1)));
        assertThat(visibility.contribution()).isEqualTo(VisibilityContribution.PARTIAL);
        assertThat(visibility.sourceRange()).isEqualTo(sourceWindow(MediaTime.ofRational(3, 1), MediaTime.ofRational(5, 1)));
    }

    @Test
    void boundaryAdjacentClipHasNoIntersection() {
        TimelineDocument document = document(video(
                clip("after", "art", MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1),
                        MediaTime.ZERO, MediaTime.ofRational(1, 1))));
        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ZERO, MediaTime.ofRational(1, 1));
        ClipVisibility visibility = analysis.tracks().getFirst().clips().getFirst();
        assertThat(visibility.decision()).isEqualTo(VisibilityDecision.OUTSIDE_REQUEST);
        assertThat(visibility.timelineCoverage()).isEmpty();
    }

    @Test
    void preservesGapBetweenSequentialClips() {
        TimelineDocument document = document(video(
                clip("a", "art-a", MediaTime.ZERO, MediaTime.ofRational(1, 1), MediaTime.ZERO, MediaTime.ofRational(1, 1)),
                clip("b", "art-b", MediaTime.ofRational(3, 2), MediaTime.ofRational(5, 2),
                        MediaTime.ZERO, MediaTime.ofRational(1, 1))));
        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ZERO, MediaTime.ofRational(5, 2));
        assertThat(analysis.tracks().getFirst().gaps())
                .contains(coverage(MediaTime.ofRational(1, 1), MediaTime.ofRational(3, 2)));
    }

    @Test
    void supportsNonZeroStartAndTrimmedSourceRange() {
        TimelineDocument document = document(video(
                clip("late", "art", MediaTime.ofRational(3, 1), MediaTime.ofRational(7, 1),
                        MediaTime.ofRational(3, 1), MediaTime.ofRational(7, 1))));
        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ZERO, MediaTime.ofRational(7, 1));
        ClipVisibility visibility = analysis.tracks().getFirst().clips().getFirst();
        assertThat(visibility.contribution()).isEqualTo(VisibilityContribution.FULL);
        assertThat(visibility.sourceWindow()).contains(sourceWindow(MediaTime.ofRational(3, 1), MediaTime.ofRational(7, 1)));
        assertThat(analysis.tracks().getFirst().gaps())
                .contains(coverage(MediaTime.ZERO, MediaTime.ofRational(3, 1)));
    }

    @Test
    void analyzesAudioIndependentlyFromVideo() {
        TimelineClip video = clip("v", "art-v", MediaTime.ZERO, MediaTime.ofRational(2, 1), MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TimelineClip audio = clip("a", "art-a", MediaTime.ZERO, MediaTime.ofRational(2, 1), MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TimelineDocument document = new TimelineDocument("timeline-1.0", List.of(
                new TimelineTrack("v1", "Video", TrackType.VIDEO, List.of(video)),
                new TimelineTrack("a1", "Audio", TrackType.AUDIO, List.of(audio))), TimelineMetadata.empty());
        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ZERO, MediaTime.ofRational(1, 1));
        assertThat(analysis.tracks()).hasSize(2);
        assertThat(analysis.tracks().get(0).trackType()).isEqualTo(TrackType.VIDEO);
        assertThat(analysis.tracks().get(1).trackType()).isEqualTo(TrackType.AUDIO);
        assertThat(analysis.tracks().get(0).clips().getFirst().decodeDependency().required()).isTrue();
        assertThat(analysis.tracks().get(1).clips().getFirst().decodeDependency().required()).isTrue();
    }

    @Test
    void keepsBothTracksWhenStackingIsUnproven() {
        TimelineClip lower = clip("lower", "art-l", MediaTime.ZERO, MediaTime.ofRational(2, 1), MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TimelineClip upper = clip("upper", "art-u", MediaTime.ZERO, MediaTime.ofRational(2, 1), MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TimelineDocument document = new TimelineDocument("timeline-1.0", List.of(
                new TimelineTrack("v1", "V1", TrackType.VIDEO, List.of(lower)),
                new TimelineTrack("v2", "V2", TrackType.VIDEO, List.of(upper))), TimelineMetadata.empty());
        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ZERO, MediaTime.ofRational(2, 1));
        assertThat(analysis.tracks()).allMatch(t -> !t.stackingProven());
        assertThat(analysis.crossTrackPruningEnabled()).isFalse();
        assertThat(analysis.globalReasons()).anyMatch(r -> r.startsWith("CROSS_TRACK_PRUNING_DISABLED"));
    }

    @Test
    void keepsUnknownEffectsAndNonIdentityMappings() {
        TimelineClip effectClip = new TimelineClip("effect", "art", "stream", "art", "digest",
                MediaTime.ZERO, MediaTime.ofRational(1, 1), MediaTime.ZERO, MediaTime.ofRational(1, 1),
                "MEDIA_STREAM", null, List.of(new TimelineClipEffect("e", "video.opacity", java.util.Map.of("opacity", 0.5))));
        TimelineClip rateClip = new TimelineClip("rate", "art", "stream", "art", "digest",
                MediaTime.ZERO, MediaTime.ofRational(1, 1), MediaTime.ZERO, MediaTime.ofRational(2, 1),
                "MEDIA_STREAM", ConstantRateTemporalMapping.of(2, 1, PlaybackDirection.FORWARD), List.of());
        TimelineDocument document = new TimelineDocument("timeline-1.0", List.of(
                new TimelineTrack("v1", "V1", TrackType.VIDEO, List.of(effectClip)),
                new TimelineTrack("v2", "V2", TrackType.VIDEO, List.of(rateClip))),
                TimelineMetadata.empty());
        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ZERO, MediaTime.ofRational(1, 1));
        assertThat(analysis.tracks()).flatExtracting(TrackVisibility::clips)
                .allSatisfy(clip -> assertThat(clip.decision()).isEqualTo(VisibilityDecision.KEEP_UNKNOWN));
    }

    @Test
    void decodeDependencyCannotBecomeAGlobalDeleteCandidate() {
        TimelineDocument document = document(video(
                clip("outside", "art", MediaTime.ofRational(2, 1), MediaTime.ofRational(3, 1),
                        MediaTime.ZERO, MediaTime.ofRational(1, 1))));
        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ZERO, MediaTime.ofRational(1, 1));
        ClipVisibility visibility = analysis.tracks().getFirst().clips().getFirst();
        assertThat(visibility.decision()).isEqualTo(VisibilityDecision.OUTSIDE_REQUEST);
        assertThat(visibility.decodeDependency().required()).isTrue();
        assertThat(analysis.candidates()).isEmpty();
    }

    @Test
    void isDeterministicAndRequestSensitiveWithoutMutatingInput() throws Exception {
        String before = TimelineDocumentJsonSerializer.serialize(loadPersisted());
        TimelineDocument document = loadPersisted();
        TemporalVisibilityAnalysis first = analyze(document, MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TemporalVisibilityAnalysis second = analyze(document, MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TemporalVisibilityAnalysis partial = analyze(document, MediaTime.ZERO, MediaTime.ofRational(1, 1));
        assertThat(first.analysisDigest()).isEqualTo(second.analysisDigest());
        assertThat(first.analysisDigest()).isNotEqualTo(partial.analysisDigest());
        assertThat(TimelineDocumentJsonSerializer.serialize(document)).isEqualTo(before);
    }

    @Test
    void firstRenderBaselineAnalyzesFullAndBothHalves() throws Exception {
        TimelineDocument document = loadPersisted();
        TemporalVisibilityAnalysis full = analyze(document, MediaTime.ZERO, MediaTime.ofRational(2, 1));
        assertThat(full.tracks()).hasSize(1);
        assertThat(full.tracks().getFirst().clips()).hasSize(2);
        assertThat(full.tracks().getFirst().gaps())
                .contains(coverage(MediaTime.ofRational(1, 2), MediaTime.ofRational(1, 1)));

        TemporalVisibilityAnalysis firstHalf = analyze(document, MediaTime.ZERO, MediaTime.ofRational(1, 1));
        assertThat(firstHalf.tracks().getFirst().clips().get(1).decision())
                .isEqualTo(VisibilityDecision.OUTSIDE_REQUEST);

        TemporalVisibilityAnalysis secondHalf = analyze(document, MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1));
        assertThat(secondHalf.tracks().getFirst().clips().get(0).decision())
                .isEqualTo(VisibilityDecision.OUTSIDE_REQUEST);
        assertThat(secondHalf.tracks().getFirst().clips().get(1).contribution())
                .isEqualTo(VisibilityContribution.FULL);
    }

    @Test
    void usesTheRequestFrameGridForSampleTimes() {
        TimelineDocument document = document(video(
                clip("clip", "art", MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1),
                        MediaTime.ZERO, MediaTime.ofRational(1, 1))));
        RenderRequest request = new RenderRequest(new RenderRequestId("request-2fps"),
                new RenderExtent(MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1), FrameRate.of(2, 1)), List.of());
        TemporalVisibilityAnalysis analysis = analyzer.analyze(document,
                new VisibilityRequest("tenant", "project", "revision", request));
        assertThat(analysis.tracks().getFirst().clips().getFirst().frameSamples())
                .extracting(FrameSample::outputFrameIndex, FrameSample::outputTime, FrameSample::sourceTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, MediaTime.ofRational(1, 1), MediaTime.ZERO),
                        org.assertj.core.groups.Tuple.tuple(1, MediaTime.ofRational(3, 2), MediaTime.ofRational(1, 2)));
    }

    @Test
    void keepsClipsReferencedByTransitionsOrAutomations() {
        TimelineClip first = clip("a", "art-a", MediaTime.ZERO, MediaTime.ofRational(1, 1),
                MediaTime.ZERO, MediaTime.ofRational(1, 1));
        TimelineClip second = clip("b", "art-b", MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1),
                MediaTime.ZERO, MediaTime.ofRational(1, 1));
        CanonicalTransition transition = new CanonicalTransition("t", "def", "1", "a", "b", "VIDEO",
                MediaTime.ofRational(1, 2), "CENTER", "LINEAR", java.util.Map.of());
        CanonicalAutomationCurve automation = new CanonicalAutomationCurve(
                "auto", "a", "video.opacity", "double", "HOLD", List.of());
        TimelineDocument document = new TimelineDocument("timeline-1.0",
                List.of(new TimelineTrack("v", "Video", TrackType.VIDEO, List.of(first, second))),
                TimelineMetadata.empty(), AudioMix.EMPTY, List.of(), List.of(),
                List.of(transition), List.of(automation));
        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ZERO, MediaTime.ofRational(2, 1));
        assertThat(analysis.tracks().getFirst().clips())
                .allSatisfy(clip -> assertThat(clip.decision()).isEqualTo(VisibilityDecision.KEEP_UNKNOWN));
    }

    @Test
    void keepsNonMediaStreamAndUnsupportedTrackClips() {
        TimelineClip image = new TimelineClip("image", "art", "stream", "art", "digest",
                MediaTime.ZERO, MediaTime.ofRational(1, 1), MediaTime.ZERO, MediaTime.ofRational(1, 1),
                "IMAGE", null, List.of());
        TimelineDocument document = new TimelineDocument("timeline-1.0", List.of(
                new TimelineTrack("v", "Video", TrackType.VIDEO, List.of(image)),
                new TimelineTrack("e", "Effect", TrackType.EFFECT, List.of())), TimelineMetadata.empty());
        TemporalVisibilityAnalysis analysis = analyze(document, MediaTime.ZERO, MediaTime.ofRational(1, 1));
        assertThat(analysis.tracks().getFirst().clips().getFirst().decision())
                .isEqualTo(VisibilityDecision.KEEP_UNKNOWN);
    }

    @Test
    void rejectsSameTrackOverlapRatherThanInventingComposition() {
        TimelineDocument document = document(video(
                clip("a", "art-a", MediaTime.ZERO, MediaTime.ofRational(2, 1), MediaTime.ZERO, MediaTime.ofRational(2, 1)),
                clip("b", "art-b", MediaTime.ofRational(1, 1), MediaTime.ofRational(3, 1), MediaTime.ZERO, MediaTime.ofRational(2, 1))));
        assertThatThrownBy(() -> analyze(document, MediaTime.ZERO, MediaTime.ofRational(3, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    private TemporalVisibilityAnalysis analyze(TimelineDocument document, MediaTime start, MediaTime end) {
        RenderRequest request = new RenderRequest(
                new RenderRequestId("request"),
                new RenderExtent(start, end, FPS), List.of());
        return analyzer.analyze(document, new VisibilityRequest("tenant", "project", "revision", request));
    }

    private static RenderExecutionCoverage coverage(MediaTime start, MediaTime end) {
        return new RenderExecutionCoverage(start, end, FPS);
    }

    private static RenderSampleWindow sourceWindow(MediaTime start, MediaTime end) {
        return new RenderSampleWindow(start, end, FPS);
    }

    private static TimelineDocument document(TimelineTrack... tracks) {
        return new TimelineDocument("timeline-1.0", List.of(tracks), TimelineMetadata.empty());
    }

    private static TimelineTrack video(TimelineClip... clips) {
        return new TimelineTrack("v1", "Video", TrackType.VIDEO, List.of(clips));
    }

    private static TimelineClip clip(String id, String artifact, MediaTime start, MediaTime end,
                                     MediaTime sourceStart, MediaTime sourceEnd) {
        return new TimelineClip(id, artifact, artifact + ":s0", artifact, "digest-" + id,
                start, end, sourceStart, sourceEnd, "MEDIA_STREAM");
    }

    private static TimelineDocument loadPersisted() throws Exception {
        try (InputStream input = TemporalVisibilityAnalyzerTest.class.getResourceAsStream(
                "/visibility/e2e14-rev8-canonical-timeline.json")) {
            assertThat(input).isNotNull();
            return TimelineDocumentJsonSerializer.deserialize(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
