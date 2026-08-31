package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regression guard: persisted merge input/output has one TimelineDocument codec. */
class TimelineMergePayloadContractRegressionTest {

    @Test
    void productionPayloadUsesTimelineDocumentReaderValidatorAndMergeBridge() {
        TimelineDocument original = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "Video", TrackType.VIDEO,
                        List.of(new TimelineClip(
                                "clip-1", "asset-1", null, null, null,
                                MediaTime.ZERO, MediaTime.ofMillis(1_000),
                                MediaTime.ZERO, MediaTime.ofMillis(1_000), "MEDIA_STREAM")))),
                TimelineMetadata.empty());

        String payload = TimelineDocumentJsonSerializer.serialize(original);
        TimelineDocument decoded = TimelineDocumentJsonSerializer.deserialize(payload);
        var candidate = TimelineDocumentCandidateMapper.map("project-1", decoded);

        assertFalse(TimelineCanonicalValidator.validate(candidate).hasFatalErrors());
        assertFalse(TimelineCanonicalNormalizer.normalize(candidate).isEmpty());
        assertEquals(payload, TimelineDocumentJsonSerializer.serialize(
                TimelineSnapshotConverter.toDocument(
                        TimelineSnapshotConverter.toSnapshot(decoded, "revision-1"))));
    }
}
