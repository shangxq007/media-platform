package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineRevisionDiffServiceTest {

    private final TimelineRevisionDiffService diffService = new TimelineRevisionDiffService();

    @Test
    void summarizesClipAndTrackChanges() {
        String from = payload("clip-from", "asset-from");
        String to = payload("clip-to", "asset-to");

        TimelineRevisionDiffService.ChangeSummary summary = diffService.summarize(from, to);
        assertTrue(summary.supported());
        assertEquals(1, summary.tracksModified());
        assertEquals(1, summary.clipsRemoved());
        assertEquals(1, summary.clipsAdded());
        assertEquals(1, summary.assetsRemoved());
        assertEquals(1, summary.assetsAdded());

        TimelineRevisionDiffService.DetailedCompare forward = diffService.compare(from, to);
        assertTrue(forward.supported());
        assertEquals(List.of(
                new TimelineRevisionDiffService.EntityChange("clip", "clip-from", "removed"),
                new TimelineRevisionDiffService.EntityChange("clip", "clip-to", "added"),
                new TimelineRevisionDiffService.EntityChange("asset", "asset-from", "removed"),
                new TimelineRevisionDiffService.EntityChange("asset", "asset-to", "added")),
                forward.entities().stream()
                        .filter(change -> !"track".equals(change.kind()))
                        .toList(),
                "compare must describe the requested from->to pair");
    }

    private static String payload(String clipId, String assetId) {
        TimelineClip clip = new TimelineClip(
                clipId, assetId, null, null, null,
                MediaTime.ZERO, MediaTime.ofMillis(1_000),
                MediaTime.ZERO, MediaTime.ofMillis(1_000), "MEDIA_STREAM");
        TimelineDocument document = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "Video", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty());
        return TimelineDocumentJsonSerializer.serialize(document);
    }
}
