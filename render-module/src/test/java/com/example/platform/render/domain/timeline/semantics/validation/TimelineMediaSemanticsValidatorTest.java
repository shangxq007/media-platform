package com.example.platform.render.domain.timeline.semantics.validation;

import com.example.platform.render.testsupport.TestSourceBindings;

import com.example.platform.render.domain.timeline.semantics.automation.Automation;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.render.domain.timeline.semantics.effect.EffectInstance;
import com.example.platform.render.domain.timeline.semantics.error.TimelineError;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.render.domain.timeline.semantics.transition.TransitionInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimelineMediaSemanticsValidatorTest {

    private static MediaClip clip(String id, String trackId,
                                   long timelineStart, long timelineEnd,
                                   long sourceStart, long sourceEnd,
                                   long rateNum, long rateDen) {
        return new MediaClip(
            id, trackId,
            new MediaClip.TimeRange(
                MediaTime.ofRational(timelineStart, 1),
                MediaTime.ofRational(timelineEnd, 1)),
            new MediaClip.TimeRange(
                MediaTime.ofRational(sourceStart, 1),
                MediaTime.ofRational(sourceEnd, 1)),
            new MediaClip.Rational(rateNum, rateDen),
            TestSourceBindings.of("asset-" + id, "stream-1", "artifact-1",
                new MediaClip.TimeRange(
                    MediaTime.ofRational(sourceStart, 1),
                    MediaTime.ofRational(sourceEnd, 1)))
        );
    }

    @Test
    @DisplayName("Valid timeline passes all predicates")
    void validTimeline() {
        MediaClip clip1 = clip("clip-1", "track-1", 0, 5, 0, 10, 2, 1);
        MediaClip clip2 = clip("clip-2", "track-1", 5, 10, 0, 10, 2, 1);

        TransitionInstance tx = new TransitionInstance(
            "tx-1", "def-1", "1.0",
            "clip-1", "clip-2",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1, clip2),
            List.of(tx),
            List.of(),
            List.of(),
            "timeline-semantics-v1"
        );

        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        assertTrue(result.isValid());
        assertTrue(result.charterPredicates().values().stream().allMatch(Boolean::booleanValue));
    }

    @Test
    @DisplayName("Duplicate clip IDs are detected")
    void duplicateClipIds() {
        MediaClip clip1 = clip("clip-1", "track-1", 0, 5, 0, 10, 2, 1);
        MediaClip clip2 = clip("clip-1", "track-2", 5, 10, 0, 10, 2, 1);

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1, clip2), List.of(), List.of(), List.of(), "timeline-semantics-v1");

        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Overlapping clips in same track are detected")
    void overlappingClips() {
        MediaClip clip1 = clip("clip-1", "track-1", 0, 6, 0, 10, 2, 1);
        MediaClip clip2 = clip("clip-2", "track-1", 5, 10, 0, 10, 2, 1);

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1, clip2), List.of(), List.of(), List.of(), "timeline-semantics-v1");

        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Transition with missing endpoint is detected")
    void transitionMissingEndpoint() {
        MediaClip clip1 = clip("clip-1", "track-1", 0, 5, 0, 10, 2, 1);

        TransitionInstance tx = new TransitionInstance(
            "tx-1", "def-1", "1.0",
            "clip-1", "nonexistent-clip",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1), List.of(tx), List.of(), List.of(), "timeline-semantics-v1");

        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.code() == TimelineError.ErrorCode.TIMELINE_TRANSITION_ENDPOINT_NOT_FOUND));
    }

    @Test
    @DisplayName("Transition across tracks is detected")
    void transitionCrossTrack() {
        MediaClip clip1 = clip("clip-1", "track-1", 0, 5, 0, 10, 2, 1);
        MediaClip clip2 = clip("clip-2", "track-2", 5, 10, 0, 10, 2, 1);

        TransitionInstance tx = new TransitionInstance(
            "tx-1", "def-1", "1.0",
            "clip-1", "clip-2",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1, clip2), List.of(tx), List.of(), List.of(), "timeline-semantics-v1");

        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.code() == TimelineError.ErrorCode.TIMELINE_TRANSITION_ENDPOINT_INCOMPATIBLE));
    }

    @Test
    @DisplayName("Duplicate transition at same cut is detected")
    void duplicateTransitionAtCut() {
        MediaClip clip1 = clip("clip-1", "track-1", 0, 5, 0, 10, 2, 1);
        MediaClip clip2 = clip("clip-2", "track-1", 5, 10, 0, 10, 2, 1);

        TransitionInstance tx1 = new TransitionInstance(
            "tx-1", "def-1", "1.0", "clip-1", "clip-2",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );

        TransitionInstance tx2 = new TransitionInstance(
            "tx-2", "def-2", "1.0", "clip-1", "clip-2",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 4),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1, clip2), List.of(tx1, tx2), List.of(), List.of(), "timeline-semantics-v1");

        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.code() == TimelineError.ErrorCode.TIMELINE_TRANSITION_DUPLICATE_AT_CUT));
    }

    @Test
    @DisplayName("Automation with unknown target is detected")
    void automationUnknownTarget() {
        MediaClip clip1 = clip("clip-1", "track-1", 0, 5, 0, 10, 2, 1);

        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ZERO, 0.0, Automation.InterpolationMode.LINEAR);
        Automation.Keyframe k2 = new Automation.Keyframe(
            "kf-2", MediaTime.ofRational(5, 1), 1.0, Automation.InterpolationMode.LINEAR);

        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "unknown-clip", "opacity", "float",
            List.of(k1, k2), Automation.ExtrapolationMode.HOLD);

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1), List.of(), List.of(), List.of(curve), "timeline-semantics-v1");

        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.code() == TimelineError.ErrorCode.TIMELINE_AUTOMATION_TARGET_NOT_FOUND));
    }

    @Test
    @DisplayName("Valid automation targeting a clip passes")
    void validAutomation() {
        MediaClip clip1 = clip("clip-1", "track-1", 0, 5, 0, 10, 2, 1);

        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ZERO, 0.0, Automation.InterpolationMode.HOLD);
        Automation.Keyframe k2 = new Automation.Keyframe(
            "kf-2", MediaTime.ofRational(5, 1), 1.0, Automation.InterpolationMode.HOLD);

        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "clip-1", "opacity", "float",
            List.of(k1, k2), Automation.ExtrapolationMode.HOLD);

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1), List.of(), List.of(), List.of(curve), "timeline-semantics-v1");

        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Empty timeline is valid")
    void emptyTimelineValid() {
        TimelineSemanticModel model = TimelineSemanticModel.empty();
        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Charter predicates map contains all required entries")
    void charterPredicates() {
        TimelineSemanticModel model = TimelineSemanticModel.empty();
        TimelineMediaSemanticsValidator.ValidationResult result =
            TimelineMediaSemanticsValidator.validate(model);
        Map<String, Boolean> predicates = result.charterPredicates();
        assertTrue(predicates.containsKey("TypeValid"));
        assertTrue(predicates.containsKey("ReferentialValid"));
        assertTrue(predicates.containsKey("ContainmentValid"));
        assertTrue(predicates.containsKey("TemporalValid"));
        assertTrue(predicates.containsKey("TransitionValid"));
        assertTrue(predicates.containsKey("EffectValid"));
        assertTrue(predicates.containsKey("AutomationValid"));
        assertTrue(predicates.containsKey("OrderingValid"));
        assertTrue(predicates.containsKey("Canonical"));
    }
}
