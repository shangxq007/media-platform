package com.example.platform.timeline.app;

import com.example.platform.timeline.version.TimelineRevision;

/**
 * ROADMAP20 B2: narrow persistence port for the canonical revision ROW insert.
 * The production implementation is the jOOQ insert into {@code timeline_revision}
 * ({@link DefaultTimelineRevisionPersistence}). Bounded failure injection for
 * TX3: a test may substitute a failing implementation WITHOUT any testMode
 * boolean or production debug branch — the production default remains the
 * single jOOQ writer.
 */
public interface TimelineRevisionPersistencePort {

    /** Inserts the revision row inside the caller's physical transaction. */
    void insertRevisionTx(org.jooq.DSLContext tx, TimelineRevision revision,
                          String productId, String snapshotId, String schemaVersion,
                          String timelineContentDigest, int revisionNumber,
                          String tenantId, String source);
}
