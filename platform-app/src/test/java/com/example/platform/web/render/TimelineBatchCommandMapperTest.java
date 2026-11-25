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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

    private OperationRequest map(TimelineOperationStep operation) {
        OperationBatchRequest batch = mapper.toBatchRequest("tenant-1", "project-1",
                new TimelineBatchCommand("batch-1", BASE, HASH, null, List.of(operation)));
        assertEquals(1, batch.operations().size());
        return batch.operations().get(0);
    }
}
