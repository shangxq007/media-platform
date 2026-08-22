package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.timeline.app.TimelineImportService;
import com.example.platform.render.app.timeline.TimelineSpecImportAdapter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.adapter.TimelineSnapshotService.SnapshotInfo;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;
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
    private TimelineRevisionQueryService revisionQueryService;
    private TimelineSpecImportAdapter importAdapter;
    private TimelineImportService importService;

    private TimelineEditorSyncService syncService;

    @BeforeEach
    void setUp() {
        importAdapter = new TimelineSpecImportAdapter(new TimelineExtensionsReader());
        importService = new TimelineImportService();
        TimelineSpecResolver resolver =
                new TimelineSpecResolver(TimelineTestSupport.internalTimelineAdapter(), new TimelineScriptParser());
        syncService = new TimelineEditorSyncService(
                new TimelineConversionService(resolver, importAdapter, importService),
                new InternalTimelineToEditorConverter(),
                snapshotService,
                resolver,
                revisionQueryService);
    }

    @Test
    void pushIsNonAuthoringConversionPreview() {
        TimelineSpec spec = TimelineSpec.create("tl-push", "Push", TimelineOutputSpec.mp4_1080p30());
        String internal = importService.importTimeline(importAdapter.toRequest(spec));

        // CFRH-I1: push no longer persists a legacy revision; it is a pure
        // conversion preview. No snapshot is written, no revision is created.
        var result = syncService.push("prj_1", "ten_1", internal);

        assertTrue(result.alreadyInternal());
        assertNull(result.snapshotId(), "push must not create a snapshot");
        assertNull(result.revision(), "push must not create a revision");
        verify(snapshotService, never()).save(any(), any(), any(), any());
    }

    @Test
    void pullLatestUsesRevisionHeadWhenPresent() {
        TimelineSpec spec = TimelineSpec.create("tl-pull", "Pull", TimelineOutputSpec.mp4_1080p30());
        String internal = importService.importTimeline(importAdapter.toRequest(spec));
        when(revisionQueryService.findHead("prj_2", "ten_2"))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionInfo(
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
                        false,
                        null,
                        null,
                        null)));
        when(snapshotService.findOwnedById("prj_2", "ten_2", "snap_2"))
                .thenReturn(Optional.of(new SnapshotInfo("snap_2", "prj_2", "ten_2", internal, "internal-1.0")));

        var result = syncService.pullByProject("prj_2", "ten_2");

        assertEquals("snap_2", result.snapshotId());
        assertNotNull(result.headRevision());
        assertEquals(2, result.headRevision().revisionNumber());
        // CFRH-I1: legacy backfill write authority removed — pullByProject never
        // attempts revision-creation backfill (TimelineRevisionService no longer
        // exposes backfillHeadFromLatestSnapshot).
    }

    @Test
    void pullByProjectFallsThroughToLatestSnapshotWhenNoHead() {
        TimelineSpec spec = TimelineSpec.create("tl-pull2", "Pull2", TimelineOutputSpec.mp4_1080p30());
        String internal = importService.importTimeline(importAdapter.toRequest(spec));
        when(revisionQueryService.findHead("prj_3", "ten_3")).thenReturn(Optional.empty());
        when(snapshotService.findLatestOwnedByProject("prj_3", "ten_3"))
                .thenReturn(Optional.of(new SnapshotInfo("snap_3", "prj_3", "ten_3", internal, "internal-1.0")));

        var result = syncService.pullByProject("prj_3", "ten_3");

        assertEquals("snap_3", result.snapshotId());
        assertNull(result.headRevision());
        // backfill write authority absent by construction (CFRH-I1).
    }
}
