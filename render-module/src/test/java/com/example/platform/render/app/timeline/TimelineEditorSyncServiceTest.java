package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.TimelineSnapshotService.SnapshotInfo;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimelineEditorSyncServiceTest {

    @Mock
    private TimelineSnapshotService snapshotService;

    @Mock
    private TimelineRevisionService revisionService;

    private TimelineEditorSyncService syncService;

    @BeforeEach
    void setUp() {
        InternalTimelineWriter writer = new InternalTimelineWriter(new TimelineExtensionsReader());
        TimelineSpecResolver resolver =
                new TimelineSpecResolver(TimelineTestSupport.internalTimelineAdapter(), new TimelineScriptParser());
        syncService = new TimelineEditorSyncService(
                new TimelineConversionService(resolver, writer),
                new InternalTimelineToEditorConverter(),
                snapshotService,
                resolver,
                revisionService);
    }

    @Test
    void pushPersistsInternalSnapshotWhenRequested() {
        TimelineSpec spec = TimelineSpec.create("tl-push", "Push", TimelineOutputSpec.mp4_1080p30());
        String internal = new InternalTimelineWriter(new TimelineExtensionsReader()).toJson(spec);
        when(snapshotService.save(eq("prj_1"), eq("ten_1"), anyString(), eq("internal-1.0")))
                .thenReturn("snap_1");
        when(revisionService.recordRevision(
                        eq("prj_1"), eq("ten_1"), eq("snap_1"), anyString(), eq("push"), isNull(), isNull(), isNull()))
                .thenReturn(new TimelineRevisionService.RevisionInfo(
                        "trev_1",
                        "prj_1",
                        "ten_1",
                        null,
                        1,
                        "snap_1",
                        1,
                        "hash",
                        "internal-1.0",
                        "push",
                        null,
                        null,
                        null,
                        List.of(),
                        "{}",
                        null,
                        null));

        var result = syncService.push("prj_1", "ten_1", internal, true);

        assertEquals("snap_1", result.snapshotId());
        assertTrue(result.alreadyInternal());
        verify(snapshotService).save(eq("prj_1"), eq("ten_1"), anyString(), eq("internal-1.0"));
    }

    @Test
    void pullLatestUsesRevisionHeadWhenPresent() {
        TimelineSpec spec = TimelineSpec.create("tl-pull", "Pull", TimelineOutputSpec.mp4_1080p30());
        String internal = new InternalTimelineWriter(new TimelineExtensionsReader()).toJson(spec);
        when(revisionService.backfillHeadFromLatestSnapshot("prj_2", null)).thenReturn(Optional.empty());
        when(revisionService.findHead("prj_2"))
                .thenReturn(Optional.of(new TimelineRevisionService.RevisionInfo(
                        "trev_2",
                        "prj_2",
                        "ten_2",
                        null,
                        2,
                        "snap_2",
                        1,
                        "hash",
                        "internal-1.0",
                        "sync",
                        null,
                        null,
                        null,
                        List.of(),
                        "{}",
                        null,
                        null)));
        when(snapshotService.findById("snap_2"))
                .thenReturn(Optional.of(new SnapshotInfo("snap_2", "prj_2", "ten_2", internal, "internal-1.0")));

        var result = syncService.pullByProject("prj_2");

        assertEquals("snap_2", result.snapshotId());
        assertNotNull(result.headRevision());
        assertEquals(2, result.headRevision().revisionNumber());
    }
}
