package com.example.platform.timeline.version;

import java.util.Optional;

/**
 * ROADMAP20 FINAL (F1): persistence contract for the revision-owned semantic
 * context (revctx_ rows in {@code timeline_snapshot}, V1-only Flyway
 * governance).
 *
 * <p>Ownership
 * (REVISION_SEMANTIC_CONTEXT_HAS_EXPLICIT_OWNERSHIP_V1): every durable
 * operation carries (projectId, tenantId). Lookup is project- AND
 * tenant-scoped (REVISION_SEMANTIC_CONTEXT_LOOKUP_IS_PROJECT_AND_TENANT_SCOPED_V1);
 * cross-project / cross-tenant context authority is forbidden
 * (CROSS_PROJECT_REVISION_CONTEXT_AUTHORITY_IS_FORBIDDEN_V1,
 * CROSS_TENANT_REVISION_CONTEXT_AUTHORITY_IS_FORBIDDEN_V1). No
 * revisionId-only authoritative lookup exists.
 *
 * <p>The durable implementation is {@code JdbcTimelineRevisionSemanticContextStore}.
 */
public interface TimelineRevisionSemanticContextStore {

    /**
     * Persists the revision semantic context in the caller's physical
     * transaction, bound to (projectId, tenantId, revisionId). tenantId MUST
     * NOT be null. Existing rows are fully deserialized and verified
     * (ownership, effect reference, recomputed digest, supported contract) —
     * exact identical context is idempotent; any difference FAILS CLOSED.
     */
    void storeTx(org.jooq.DSLContext tx, String projectId, String tenantId,
                 String revisionId, TimelineRevisionSemanticContext semanticContext);

    /**
     * Ownership-scoped exact lookup: the context is resolved only if it
     * belongs to (projectId, tenantId, revisionId). No revisionId-only
     * authoritative lookup.
     */
    Optional<TimelineRevisionSemanticContext> findByRevisionId(
            org.jooq.DSLContext readDsl, String projectId, String tenantId, String revisionId);
}
