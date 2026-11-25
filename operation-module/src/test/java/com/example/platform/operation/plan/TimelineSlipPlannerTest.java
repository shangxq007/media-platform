package com.example.platform.operation.plan;

import com.example.platform.operation.operation.*;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.*;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineSlipPlannerTest {
    @Test
    void rejectsReverseAndNonUnitMappingsAndMismatchedDurations() {
        for (var mapping : List.of(ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.REVERSE),
                ConstantRateTemporalMapping.of(2, 1, PlaybackDirection.FORWARD))) {
            var base = fixture(mapping, 5);
            assertTrue(assertThrows(PlanException.class, () -> plan(base, "1")).getMessage().contains("forward 1x"));
        }
        var base = fixture(ConstantRateTemporalMapping.identity(), 4);
        assertTrue(assertThrows(PlanException.class, () -> plan(base, "-1")).getMessage().contains("matching source and timeline durations"));
    }

    @Test
    void rejectsAffectedAutomationAndUnrepresentableOrMalformedOffsets() {
        var base = fixture(ConstantRateTemporalMapping.identity(), 5);
        var curve = new CanonicalAutomationCurve("curve", "selected", "gain", "NUMBER", "HOLD", List.of());
        var automated = new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(),
                base.getAudioMix(), List.of(), List.of(), List.of(), List.of(curve));
        assertTrue(assertThrows(PlanException.class, () -> plan(automated, "1")).getMessage().contains("automation"));
        assertThrows(PlanException.class, () -> plan(base, Long.toString(Long.MAX_VALUE)));
        for (String bad : List.of("+-1", "--2/3", "1/0", "1.5"))
            assertThrows(IllegalArgumentException.class, () -> OperationParameters.SlipParameters.parse(bad));
        assertEquals(OperationParameters.SlipParameters.parse("0"), OperationParameters.SlipParameters.parse("-0/30"));
        assertTrue(plan(base, "0").noOp());
    }

    private static TimelineDocument fixture(ConstantRateTemporalMapping mapping, int sourceEnd) {
        var clip = new TimelineClip("selected", "asset", "stream", "artifact", "a".repeat(64),
                MediaTime.ofRational(5, 1), MediaTime.ofRational(8, 1), MediaTime.ofRational(2, 1),
                MediaTime.ofRational(sourceEnd, 1), "MEDIA_STREAM", mapping);
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video", "Video", TrackType.VIDEO, List.of(clip))), TimelineMetadata.empty());
    }

    private OperationPlan plan(TimelineDocument base, String offset) {
        var def = OperationDefinition.V3.SLIP_CLIP;
        String hash = new TimelineContentDigester().digest(base);
        var target = new OperationTargetRequest.ClipSelectionTargetRequest("project",
                new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of("selected"))), SelectionSpec.ExpansionPolicy.EXACT);
        var request = new OperationRequest(def.definitionId(), def.version(), target,
                OperationParameters.SlipParameters.parse(offset), "base", hash, null);
        return new OperationPlanner().plan(OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext("base", hash, base, "project")), "base", base);
    }
}
