package com.example.platform.timeline.version;

import java.util.Optional;

/**
 * ROADMAP20 B2/B4: persistence contract for the revision-owned semantic
 * context (revctx_ rows in {@code timeline_snapshot}, V1-only Flyway
 * governance). The durable implementation is
 * {@code JdbcTimelineRevisionSemanticContextStore}; this interface enables
 * bounded failure injection for TX4 without any testMode in business logic.
 */
public interface TimelineRevisionSemanticContextStore {

    /**
     * Persists the revision semantic context in the caller's physical
     * transaction, bound to (projectId, revisionId).
     */
    void storeTx(org.jooq.DSLContext tx, String projectId, String revisionId,
                 TimelineRevisionSemanticContext semanticContext);

    /** Lookup within a read context (production: Jdbc store takes the DSL). */
    default Optional<TimelineRevisionSemanticContext> findByRevisionId(org.jooq.DSLContext readDsl, String revisionId) {
        return findByRevisionId(revisionId);
    }

    /** Ownership-scoped exact lookup (project binding validated by revision ownership). */
    Optional<TimelineRevisionSemanticContext> findByRevisionId(String revisionId);
}
