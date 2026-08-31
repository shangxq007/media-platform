package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.merge.TimelineMergeRequest;
import com.example.platform.timeline.diff.merge.TimelineMergeResult;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineMergeEngineTest {

    private static final String PROJECT = "project-1";
    private static final String TENANT = "tenant-1";
    private static final String BASE = "revision-base";
    private static final String SOURCE = "revision-source";
    private static final String TARGET = "revision-target";

    private TimelineRevisionRepository revisions;
    private TimelineSnapshotService snapshots;
    private TimelineRevisionSaveService saveService;
    private TimelineMergeEngine engine;

    @BeforeEach
    void setUp() {
        revisions = mock(TimelineRevisionRepository.class);
        snapshots = mock(TimelineSnapshotService.class);
        saveService = mock(TimelineRevisionSaveService.class);
        var preview = new TimelineMergePreviewService(
                new com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector());
        engine = new TimelineMergeEngine(
                revisions, snapshots, saveService, preview,
                new TimelineNonConflictingMergePlanner(preview),
                new TimelinePatchApplier(), new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(TimelineArtifactPinValidator.class),
                mock(com.example.platform.artifact.app.ArtifactPinService.class),
                mock(org.jooq.DSLContext.class));
    }

    @Test
    void semanticMergeReturnsReloadableTimelineDocumentForSourceChange() {
        stubDocuments(document(0, 1_000), document(500, 1_000), document(0, 1_000));

        TimelineMergeResult result = engine.mergeSemantic(request());

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status());
        TimelineDocument reloaded = TimelineDocumentJsonSerializer.deserialize(
                result.mergedPayloadJson());
        assertEquals(MediaTime.ofMillis(500),
                reloaded.getTracks().getFirst().clips().getFirst().getStartTime());
    }

    @Test
    void persistentMergeLowersToSoleBoundaryWithTargetThenSourceParents() {
        TimelineDocument expected = document(500, 1_000);
        stubDocuments(document(0, 1_000), expected, document(0, 1_000));
        when(revisions.listOwnedByProject(PROJECT, TENANT, null, null, null, 500))
                .thenReturn(List.of());
        var persisted = mock(com.example.platform.timeline.version.TimelineRevision.class);
        when(persisted.revisionId()).thenReturn("revision-merge");
        when(saveService.saveMergeRevision(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                any(TimelineDocument.class), anyString())).thenReturn(persisted);

        TimelineMergeResult result = engine.merge(request());

        assertEquals("revision-merge", result.mergedRevisionId());
        assertNotNull(TimelineDocumentJsonSerializer.deserialize(result.mergedPayloadJson()));
        verify(saveService).saveMergeRevision(
                org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.eq(PROJECT),
                org.mockito.ArgumentMatchers.eq(TARGET),
                org.mockito.ArgumentMatchers.eq(SOURCE),
                org.mockito.ArgumentMatchers.eq(BASE),
                org.mockito.ArgumentMatchers.argThat(document ->
                        TimelineDocumentJsonSerializer.serialize(document)
                                .equals(result.mergedPayloadJson())),
                org.mockito.ArgumentMatchers.eq("server-user"));
    }

    private void stubDocuments(
            TimelineDocument base, TimelineDocument source, TimelineDocument target) {
        stubRevision(BASE, "snapshot-base", base);
        stubRevision(SOURCE, "snapshot-source", source);
        stubRevision(TARGET, "snapshot-target", target);
    }

    private void stubRevision(String revisionId, String snapshotId, TimelineDocument document) {
        when(revisions.findOwnedById(revisionId, PROJECT, TENANT))
                .thenReturn(Optional.of(new TimelineRevisionRepository.RevisionRow(
                        revisionId, PROJECT, TENANT, null, 1, snapshotId, 0,
                        new com.example.platform.timeline.canonical.TimelineContentDigester().digest(document),
                        TimelineDocument.CURRENT_SCHEMA_VERSION, "test", "server-user",
                        null, null, null, null, null, false, null, null, OffsetDateTime.now())));
        when(snapshots.findOwnedById(PROJECT, TENANT, snapshotId))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo(
                        snapshotId, PROJECT, TENANT,
                        TimelineDocumentJsonSerializer.serialize(document),
                        TimelineDocument.CURRENT_SCHEMA_VERSION)));
    }

    private static TimelineMergeRequest request() {
        return new TimelineMergeRequest(
                PROJECT, TENANT, BASE, SOURCE, TARGET, "server-user", "merge");
    }

    private static TimelineDocument document(long startMillis, long durationMillis) {
        TimelineClip clip = new TimelineClip(
                "clip-1", "asset-1", null, null, null,
                MediaTime.ofMillis(startMillis), MediaTime.ofMillis(startMillis + durationMillis),
                MediaTime.ZERO, MediaTime.ofMillis(durationMillis), "MEDIA_STREAM");
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "Video", TrackType.VIDEO, List.of(clip))),
                new TimelineMetadata("", "", Map.of()));
    }
}
