package com.example.platform.operation.plan;

import com.example.platform.operation.operation.*;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.*;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.timeline.semantics.effect.OpacityKeys;
import com.example.platform.timeline.semantics.effect.VideoTransform;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineOpacityPlannerTest {
    private static final OpacityKeys KEYS = new OpacityKeys(MediaTime.parse("3/1"), 0, MediaTime.parse("5/1"), 1);

    @Test
    void editsOnlyTheSelectedChannelAndRetainsItsStableIdentities() {
        var original = KEYS.curve("user-curve", "grade", "first", "second");
        var other = new CanonicalAutomationCurve("contrast-channel", "grade", "contrast", "float", "HOLD", original.keyframes());
        var base = document(original, other);
        assertTrue(plan(base, KEYS).noOp());
        var changed = new OpacityKeys(MediaTime.parse("91/30"), 0.4, KEYS.lastTime(), 0.8);
        var candidate = plan(base, changed).candidateTimeline();
        assertEquals(List.of(other, changed.curve("user-curve", "grade", "first", "second")), candidate.getAutomations());
        assertEquals(base.getTracks(), candidate.getTracks());
    }

    @Test
    void refusesToReplaceNonRepresentableSavedKeys() {
        var original = KEYS.curve("saved", "grade", "a", "b");
        var outside = new OpacityKeys(MediaTime.ZERO, 0, KEYS.lastTime(), 1).curve("saved", "grade", "a", "b");
        var multi = new CanonicalAutomationCurve("saved", "grade", "opacity", "float", "HOLD", List.of(original.keyframes().getFirst()));
        for (var curve : List.of(outside, multi)) {
            var base = document(curve);
            assertThrows(PlanException.class, () -> plan(base, KEYS));
            assertEquals(List.of(curve), base.getAutomations());
        }
    }

    private static TimelineDocument document(CanonicalAutomationCurve... curves) {
        var effect = new TimelineClipEffect("grade", BasicVideoEffect.EFFECT_KEY, BasicVideoEffect.IDENTITY.toParameters());
        var clip = new TimelineClip("red", "asset", "stream", "artifact", "a".repeat(64), MediaTime.parse("2/1"),
                MediaTime.parse("6/1"), MediaTime.ZERO, MediaTime.parse("4/1"), "MEDIA_STREAM",
                ConstantRateTemporalMapping.identity(), List.of(effect), VideoTransform.IDENTITY);
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("v", "Video", TrackType.VIDEO, List.of(clip))), TimelineMetadata.empty(),
                null, List.of(), List.of(), List.of(), List.of(curves));
    }

    private static OperationPlan plan(TimelineDocument base, OpacityKeys keys) {
        var def = OperationDefinition.V3.SET_OPACITY_KEYS;
        var selection = new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of("red")));
        var target = new OperationTargetRequest.ClipSelectionTargetRequest("project", selection, SelectionSpec.ExpansionPolicy.EXACT);
        var hash = new TimelineContentDigester().digest(base);
        var request = new OperationRequest(def.definitionId(), def.version(), target, new OperationParameters.OpacityParameters(keys),
                "base", hash, null);
        return new OperationPlanner().plan(OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext("base", hash, base, "project")), "base", base);
    }
}
