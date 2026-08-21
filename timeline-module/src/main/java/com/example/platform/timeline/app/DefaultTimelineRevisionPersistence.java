package com.example.platform.timeline.app;

import com.example.platform.timeline.version.TimelineRevision;
import java.time.LocalDateTime;
import org.jooq.DSLContext;

import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;

/**
 * Production jOOQ implementation of {@link TimelineRevisionPersistencePort}:
 * the SINGLE canonical {@code timeline_revision} row insert. No alternate
 * persistence path exists in production.
 */
public final class DefaultTimelineRevisionPersistence implements TimelineRevisionPersistencePort {

    @Override
    public void insertRevisionTx(DSLContext tx, TimelineRevision revision,
                                 String productId, String snapshotId, String schemaVersion,
                                 int revisionNumber, String tenantId, String source) {
        tx.insertInto(TIMELINE_REVISION)
                .set(TIMELINE_REVISION.ID, revision.revisionId())
                .set(TIMELINE_REVISION.PROJECT_ID, productId)
                .set(TIMELINE_REVISION.PARENT_REVISION_ID, revision.parentRevisionId())
                .set(TIMELINE_REVISION.REVISION_NUMBER, revisionNumber)
                .set(TIMELINE_REVISION.TENANT_ID, tenantId)
                .set(TIMELINE_REVISION.SNAPSHOT_ID, snapshotId)
                .set(TIMELINE_REVISION.INTERNAL_REVISION, revisionNumber)
                .set(TIMELINE_REVISION.CONTENT_HASH, revision.contentDigest())
                .set(TIMELINE_REVISION.SCHEMA_VERSION, schemaVersion)
                .set(TIMELINE_REVISION.CREATED_AT, LocalDateTime.now())
                .set(TIMELINE_REVISION.SOURCE, source)
                .execute();
    }
}
