package com.example.platform.render.domain.timeline.semantics.duration;

import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.render.domain.timeline.semantics.time.MediaTime;
import com.example.platform.render.domain.timeline.semantics.transition.TransitionInstance;
import com.example.platform.render.domain.timeline.semantics.validation.TimelineSemanticModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimelineDurationCalculatorTest {

    private static MediaClip clip(String id, long tStart, long tEnd, long sStart, long sEnd) {
        return new MediaClip(
            id, "track-1",
            new MediaClip.TimeRange(
                MediaTime.ofRational(tStart, 1), MediaTime.ofRational(tEnd, 1)),
            new MediaClip.TimeRange(
                MediaTime.ofRational(sStart, 1), MediaTime.ofRational(sEnd, 1)),
            new MediaClip.Rational(1, 1),
            "asset-" + id
        );
    }

    @Test
    @DisplayName("Duration is the maximum end time of all clips")
    void durationIsMaxEndTime() {
        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip("c1", 0, 5, 0, 5), clip("c2", 5, 12, 0, 7)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        MediaTime duration = TimelineDurationCalculator.calculateDuration(model);
        assertEquals(MediaTime.ofRational(12, 1), duration);
    }

    @Test
    @DisplayName("Empty timeline has zero duration")
    void emptyTimeline() {
        MediaTime duration = TimelineDurationCalculator.calculateDuration(
            TimelineSemanticModel.empty());
        assertEquals(MediaTime.ZERO, duration);
    }

    @Test
    @DisplayName("Single clip: duration equals clip timeline duration")
    void singleClip() {
        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip("c1", 0, 8, 0, 8)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        assertEquals(MediaTime.ofRational(8, 1),
            TimelineDurationCalculator.calculateDuration(model));
    }

    @Test
    @DisplayName("INSERT_DURATION transition adds to timeline end")
    void insertDurationTransition() {
        MediaClip clip1 = clip("c1", 0, 5, 0, 5);
        MediaClip clip2 = clip("c2", 5, 10, 0, 5);

        TransitionInstance tx = new TransitionInstance(
            "tx-1", "def-1", "1.0", "c1", "c2",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(2, 1),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.INSERT_DURATION,
            null
        );

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1, clip2), List.of(tx), List.of(), List.of(),
            "timeline-semantics-v1"
        );

        // With INSERT_DURATION, timeline extends by transition duration
        MediaTime duration = TimelineDurationCalculator.calculateDuration(model);
        assertEquals(MediaTime.ofRational(12, 1), duration);
    }

    @Test
    @DisplayName("Temporal impact analysis: changing clip range")
    void temporalImpact() {
        MediaClip clip1 = clip("c1", 0, 5, 0, 5);
        MediaClip clip2 = clip("c2", 5, 10, 0, 5);

        TimelineSemanticModel before = new TimelineSemanticModel(
            List.of(clip1, clip2), List.of(), List.of(), List.of(),
            "timeline-semantics-v1"
        );

        // Modify clip1 to end at 8
        MediaClip modifiedClip1 = new MediaClip(
            "c1", "track-1",
            new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(8, 1)),
            clip1.sourceRange(), clip1.playbackRate(), clip1.mediaReference()
        );

        TimelineSemanticModel after = new TimelineSemanticModel(
            List.of(modifiedClip1, clip2), List.of(), List.of(), List.of(),
            "timeline-semantics-v1"
        );

        TimelineDurationCalculator.TemporalImpact impact =
            TimelineDurationCalculator.analyzeImpact(before, after, "c1");

        assertEquals(MediaTime.ofRational(10, 1), impact.beforeDuration());
        assertEquals(MediaTime.ofRational(10, 1), impact.afterDuration());
        assertEquals(MediaTime.ZERO, impact.durationDelta());
        assertTrue(impact.affectedClipIds().contains("c1"));
    }

    @Test
    @DisplayName("Clip contribution equals timeline duration")
    void clipContribution() {
        MediaClip clip1 = clip("c1", 2, 7, 0, 10);
        assertEquals(MediaTime.ofRational(5, 1),
            TimelineDurationCalculator.clipContribution(clip1));
    }

    @Test
    @DisplayName("hasVariableOutputDuration returns false when no such effect")
    void noVariableDuration() {
        TimelineSemanticModel model = TimelineSemanticModel.empty();
        assertFalse(TimelineDurationCalculator.hasVariableOutputDuration(model));
    }
}
