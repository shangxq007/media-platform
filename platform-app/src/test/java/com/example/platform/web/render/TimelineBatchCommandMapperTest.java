package com.example.platform.web.render;

import com.example.platform.operation.application.TimelineOperationStep;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.operation.batch.OperationBatchRequest;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.application.TimelineBatchCommand;
import com.example.platform.operation.application.TimelineBatchCommandMapper;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** REST transport coverage for the non-clip target families the planner supports. */
class TimelineBatchCommandMapperTest {

    private static final String BASE = "revision-1";
    private static final String HASH = "a".repeat(64);
    private final TimelineBatchCommandMapper mapper = new TimelineBatchCommandMapper();

    @Test
    void splitMapsItsRightIdentityAndExactTime() {
        OperationRequest request = map(new TimelineOperationStep(
                "timeline.clip.split",
                new TimelineBatchCommand.Target("clip-a", null, null, null, null),
                Map.of("rightClipId", "clip-b", "splitTimelineTime", "2/1")));

        OperationParameters.SplitParameters split =
                assertInstanceOf(OperationParameters.SplitParameters.class, request.parameters());
        assertEquals("clip-b", split.rightClipId().value());
        assertEquals("2/1", split.splitTimelineTime().toString());
    }

    @Test
    void opacityMapsBothNumericEndpointsAndRejectsValuesOutsideTheirExactBounds() {
        var target = new TimelineBatchCommand.Target("clip-a", null, null, null, null);
        var parameters = new java.util.HashMap<String, Object>(Map.of(
                "firstTime", "3/1", "lastTime", "5/1", "firstValue", 0, "lastValue", 1));
        var mapped = map(new TimelineOperationStep("timeline.clip.opacity.keyframes.set", target, parameters));
        var keys = assertInstanceOf(OperationParameters.OpacityParameters.class, mapped.parameters()).keys();
        assertEquals(0.0, keys.firstValue());
        assertEquals(1.0, keys.lastValue());
        parameters.put("lastValue", new java.math.BigDecimal("1.00000000000000000001"));
        assertThrows(IllegalArgumentException.class, () ->
                map(new TimelineOperationStep("timeline.clip.opacity.keyframes.set", target, parameters)));
    }

    @Test
    void subtitleMapsExactCueTimesAndOriginalUnicodeAndRejectsInvalidKeys() {
        String text = "春天来了 🌻\uFE0F\nSpring is here";
        var parameters = new java.util.HashMap<String, Object>(Map.of("cueId","spring","start","1/1","end","4/1",
                "text",text,"width",160,"fontSize",24,"fontProfile","noto-sc-color-emoji-v1","keys",List.of(
                        Map.of("time","0","xMicros",100000,"yMicros",400000,"opacityMicros",1000000),
                        Map.of("time","2/1","xMicros",250000,"yMicros",550000,"opacityMicros",600000))));
        var cue = assertInstanceOf(OperationParameters.SubtitleCueParameters.class,
                map(new TimelineOperationStep("timeline.subtitle.cue.set", null, parameters)).parameters());
        assertEquals(text, cue.text());
        assertEquals("1/1", cue.start().toString());
        assertEquals("2/1", cue.motion().keys().getLast().time());
        parameters.put("end","3/1");
        assertThrows(IllegalArgumentException.class,
                () -> map(new TimelineOperationStep("timeline.subtitle.cue.set", null, parameters)));
    }

    @Test
    void selectedSubtitleFadeRequiresCompleteGraphemesAndExactCueLocalTiming() {
        var parameters = new java.util.HashMap<String, Object>(Map.of("cueId","fade","start","1/1","end","5/1",
                "text","甲\r\n🌻️word","width",160,"fontSize",24,"keys",List.of()));
        String id = "timeline.subtitle.cue.set";
        parameters.put("selectedTextFade", Map.of("start",3,"end",7,"time","2/2","duration","1/1"));
        var mapped = assertInstanceOf(OperationParameters.SubtitleCueParameters.class,
                map(new TimelineOperationStep(id, null, parameters)).parameters());
        assertEquals("3;7;1/1;1/1", mapped.motion().selectedTextFade().canonical());
        assertEquals(mapped.motion().selectedTextFade(), mapped.withResolvedFont("noto-sc-color-emoji-v1").motion().selectedTextFade());
        for (var invalid : List.of(
                Map.of("start",4,"end",7,"time","1/1","duration","1/1"),
                Map.of("start",2,"end",7,"time","1/1","duration","1/1"),
                Map.of("start",3,"end",10,"time","1/1","duration","1/1"),
                Map.of("start",3,"end",7,"time","3/1","duration","2/1"),
                Map.of("start",3,"end",7,"time","0","duration","0/1"),
                Map.of("start",3,"end",7,"time","1.5","duration","1/1"))) {
            parameters.put("selectedTextFade", invalid);
            assertThrows(IllegalArgumentException.class, () -> map(new TimelineOperationStep(id, null, parameters)));
        }
    }

    @Test
    void subtitleFadeConfirmationDistinguishesOmissionFromExplicitClear() {
        var parameters = new java.util.HashMap<String, Object>(Map.of("cueId", "fade", "start", "1/1", "end", "5/1",
                "text", "FadeWord", "width", 220, "fontSize", 24, "keys", List.of()));
        String id = "timeline.subtitle.cue.set";
        var preserve = map(new TimelineOperationStep(id, null, parameters));
        parameters.put("selectedTextFade", null);
        var clear = map(new TimelineOperationStep(id, null, parameters));
        var preserved = assertInstanceOf(OperationParameters.SubtitleCueParameters.class, preserve.parameters());
        var cleared = assertInstanceOf(OperationParameters.SubtitleCueParameters.class, clear.parameters());
        assertEquals(OperationParameters.SubtitleCueParameters.FadeIntent.PRESERVE, preserved.fadeIntent());
        assertEquals(OperationParameters.SubtitleCueParameters.FadeIntent.CLEAR, cleared.fadeIntent());
        assertEquals(cleared.fadeIntent(), cleared.withResolvedFont("noto-sc-color-emoji-v1").fadeIntent());
        assertNotEquals(
                com.example.platform.operation.operation.ParameterDigest.compute(preserve.definitionId(), preserve.version(), preserved),
                com.example.platform.operation.operation.ParameterDigest.compute(clear.definitionId(), clear.version(), cleared));
        parameters.put("selectedTextFade", "clear");
        assertThrows(IllegalArgumentException.class, () -> map(new TimelineOperationStep(id, null, parameters)));
    }

    @Test
    void subtitleDecorationPinsTheAssetAndAnExactIntervalEvenWhenDisabled() {
        var fields = new java.util.HashMap<String, Object>(Map.of("cueId", "decorated", "start", "1/1", "end", "5/1",
                "text", "Anchor", "width", 240, "fontSize", 24, "keys", List.of()));
        String id = "timeline.subtitle.cue.set";
        var preserve = map(new TimelineOperationStep(id, null, fields));
        fields.put("decoration", Map.of("assetId","sunflower-v1","placement","right-of-cue-v1",
                "enabled",false,"start","2/2","end","4/2"));
        var configured = assertInstanceOf(OperationParameters.SubtitleCueParameters.class,
                map(new TimelineOperationStep(id, null, fields)).parameters());
        assertEquals("sunflower-v1;right-of-cue-v1;false;1/1;2/1", configured.motion().decoration().canonical());
        assertEquals(configured.motion().decoration(), configured.withResolvedFont("noto-sc-color-emoji-v1").motion().decoration());
        fields.put("end", "5/2");
        assertThrows(IllegalArgumentException.class, () -> map(new TimelineOperationStep(id, null, fields)));
        fields.put("end", "5/1");
        fields.put("decoration", Map.of("assetId","unregistered","placement","right-of-cue-v1",
                "enabled",true,"start","0","end","2/1"));
        assertThrows(IllegalArgumentException.class, () -> map(new TimelineOperationStep(id, null, fields)));
        fields.put("decoration", null);
        var remove = map(new TimelineOperationStep(id, null, fields));
        assertTrue(assertInstanceOf(OperationParameters.SubtitleCueParameters.class, remove.parameters()).decorationSupplied());
        assertNotEquals(
                com.example.platform.operation.operation.ParameterDigest.compute(preserve.definitionId(), preserve.version(), preserve.parameters()),
                com.example.platform.operation.operation.ParameterDigest.compute(remove.definitionId(), remove.version(), remove.parameters()));
    }

    @Test
    void wholeCueFontsNormalizeMissingAndRejectUnknownOrNullProfiles() {
        var parameters = new java.util.HashMap<String, Object>(Map.of("cueId", "mixed", "start", "0", "end", "4/1",
                "text", "春㐀🌻", "width", 200, "fontSize", 24, "keys", List.of()));
        String operation = "timeline.subtitle.cue.set";
        var missing = assertInstanceOf(OperationParameters.SubtitleCueParameters.class,
                map(new TimelineOperationStep(operation, null, parameters)).parameters());
        assertTrue(missing.useDefault());
        assertEquals("noto-sc-color-emoji-v1", missing.motion().effectiveFontProfile());
        parameters.put("fontProfile", "noto-sc-color-emoji-v1");
        var explicit = assertInstanceOf(OperationParameters.SubtitleCueParameters.class,
                map(new TimelineOperationStep(operation, null, parameters)).parameters());
        assertFalse(explicit.useDefault());
        assertNotEquals(missing, explicit);
        parameters.put("fontProfile", "noto-serif-sc-color-emoji-v1");
        var alternative = assertInstanceOf(OperationParameters.SubtitleCueParameters.class,
                map(new TimelineOperationStep(operation, null, parameters)).parameters());
        assertEquals("noto-serif-sc-color-emoji-v1", alternative.motion().fontProfile());
        parameters.put("fontProfile", "unregistered");
        assertThrows(IllegalArgumentException.class, () -> map(new TimelineOperationStep(operation, null, parameters)));
        parameters.put("fontProfile", null);
        assertThrows(IllegalArgumentException.class, () -> map(new TimelineOperationStep(operation, null, parameters)));
    }

    @Test
    void positionKeysCarryPairedExactCoordinatesAndSupportExplicitClear() {
        var target = new TimelineBatchCommand.Target("clip-a", null, null, null, null);
        var first = Map.of("time", "91/30", "xMicros", -16000000, "yMicros", 0);
        var second = Map.of("time", "7/2", "xMicros", 16000000, "yMicros", 250000);
        var id = "timeline.clip.position.keyframes.set";
        var mapped = map(new TimelineOperationStep(id, target, Map.of("keys", List.of(first, second))));
        var paired = assertInstanceOf(OperationParameters.PositionParameters.class, mapped.parameters()).keys();
        assertEquals("91/30;-16000000;0|7/2;16000000;250000", paired.canonicalSamples());
        var cleared = map(new TimelineOperationStep(id, target, Map.of("keys", List.of())));
        assertEquals(List.of(), assertInstanceOf(OperationParameters.PositionParameters.class, cleared.parameters()).keys().keys());
        for (var bad : List.of(List.of(first), List.of(first, first), List.of(second, first),
                List.of(first, Map.of("time", "7/2", "xMicros", 16000001, "yMicros", 0)),
                List.of(first, Map.of("time", "7/2", "xMicros", 0, "yMicros", new java.math.BigDecimal("1.0000000000000000001"))))) {
            assertThrows(IllegalArgumentException.class, () -> map(new TimelineOperationStep(id, target, Map.of("keys", bad))));
        }
    }

    @Test
    void groupCreateMapsItsMembersAsTheClipScope() {
        OperationRequest request = map(new TimelineOperationStep(
                "timeline.group.create", null,
                Map.of("groupId", "group-1", "members", List.of("clip-a", "clip-b"))));

        OperationTargetRequest.ClipSelectionTargetRequest target =
                assertInstanceOf(OperationTargetRequest.ClipSelectionTargetRequest.class,
                        request.target());
        SelectionSpec.ExplicitObjectSelection selection =
                assertInstanceOf(SelectionSpec.ExplicitObjectSelection.class,
                        target.selectionSpec());
        assertEquals(List.of(TimelineClipId.of("clip-a"), TimelineClipId.of("clip-b")),
                selection.clipIds());
    }

    @Test
    void audioOperationMapsItsMixInputTarget() {
        OperationRequest request = map(new TimelineOperationStep(
                "audio.gain.set",
                new TimelineBatchCommand.Target("clip-a", null, null, null, "video-main"),
                Map.of("linear", 0.5)));

        OperationTargetRequest.AudioTargetRequest target =
                assertInstanceOf(OperationTargetRequest.AudioTargetRequest.class, request.target());
        assertEquals(AudioMixInput.of("video-main", "clip-a"), target.audioMixInput());
    }

    @Test
    void groupMembershipOperationMapsItsGroupTarget() {
        OperationRequest request = map(new TimelineOperationStep(
                "timeline.group.update-members",
                new TimelineBatchCommand.Target(null, null, "group-1", null, null),
                Map.of("membersToAdd", List.of("clip-a"), "membersToRemove", List.of())));

        OperationTargetRequest.GroupTargetRequest target =
                assertInstanceOf(OperationTargetRequest.GroupTargetRequest.class, request.target());
        assertEquals("group-1", target.groupId().value());
    }

    @Test
    void syncRemoveMapsItsSyncTarget() {
        OperationRequest request = map(new TimelineOperationStep(
                "timeline.sync.remove",
                new TimelineBatchCommand.Target(null, null, null, "SYNC(clip-a,clip-b)", null),
                Map.of()));

        OperationTargetRequest.SyncTargetRequest target =
                assertInstanceOf(OperationTargetRequest.SyncTargetRequest.class, request.target());
        assertEquals("SYNC(clip-a,clip-b)", target.syncIdentityKey());
    }

    @Test
    void bindsExplicitBranchAndRetainsDefaultForExistingRequests() {
        var step = new TimelineOperationStep("timeline.trim",
                new TimelineBatchCommand.Target("clip-a", null, null, null, null),
                Map.of("edge", "START", "delta", "1/2"));
        var legacy = new TimelineBatchCommand("edit-1", BASE, HASH, null, List.of(step));
        var alternate = new TimelineBatchCommand("edit-1", BASE, HASH, null, List.of(step), "draft-a");
        assertEquals("main", mapper.toBatchRequest("tenant-1", "project-1", legacy).target().refId());
        assertEquals("draft-a", mapper.toBatchRequest("tenant-1", "project-1", alternate).target().refId());
        assertThrows(IllegalArgumentException.class, () -> mapper.toBatchRequest("tenant-1", "project-1",
                new TimelineBatchCommand("edit-1", BASE, HASH, null, List.of(step), "../other")));
    }

    private OperationRequest map(TimelineOperationStep operation) {
        OperationBatchRequest batch = mapper.toBatchRequest("tenant-1", "project-1",
                new TimelineBatchCommand("batch-1", BASE, HASH, null, List.of(operation)));
        assertEquals(1, batch.operations().size());
        return batch.operations().get(0);
    }
}
