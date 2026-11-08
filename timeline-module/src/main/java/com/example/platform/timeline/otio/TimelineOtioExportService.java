package com.example.platform.timeline.otio;

import com.example.platform.timeline.api.revision.TimelineRevisionQueries;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.canonical.TimelineDocument;
import java.util.Objects;
import java.util.Optional;

/** Read-only application service: canonical revision snapshot -> OTIO JSON. */
public final class TimelineOtioExportService {

    private final TimelineRevisionQueries revisions;
    private final CanonicalTimelineOtioExporter exporter;

    public TimelineOtioExportService(TimelineRevisionQueries revisions) {
        this(revisions, new CanonicalTimelineOtioExporter());
    }

    public TimelineOtioExportService(
            TimelineRevisionQueries revisions,
            CanonicalTimelineOtioExporter exporter) {
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    /** Export the exact persisted snapshot for one revision without mutating anything. */
    public String export(String tenantId, String projectId, String revisionId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(revisionId, "revisionId");
        TimelineRevisionQueries.RevisionSnapshotPayload payload = revisions
                .getRevisionSnapshotPayload(projectId, tenantId, revisionId)
                .orElseThrow(() -> new OtioExportException(
                        OtioExportException.Code.INVALID_TIMELINE,
                        "canonical timeline revision not found: " + revisionId));
        TimelineDocument document = TimelineDocumentJsonSerializer.deserialize(payload.canonicalTimelineJson());
        return exporter.export(document, new OtioExportIdentity(
                tenantId, projectId, revisionId, payload.schemaVersion()));
    }

    /** Read-only lookup variant; absent revisions are reported by the caller. */
    public Optional<String> exportIfPresent(String tenantId, String projectId, String revisionId) {
        if (revisions.getRevisionSnapshotPayload(projectId, tenantId, revisionId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(export(tenantId, projectId, revisionId));
    }
}
