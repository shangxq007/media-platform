package com.example.platform.timeline.adapter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.example.platform.shared.web.TenantGuard;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import org.jooq.impl.DSL;


@Repository
public class TimelineRevisionRepository {

    private final DSLContext dsl;

    public TimelineRevisionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<RevisionRow> findById(String revisionId) {
        Record row = dsl.select()
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .and(tenantCondition())
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    public Optional<RevisionRow> findHeadByProject(String projectId) {
        Record row = dsl.select()
                .from(TIMELINE_REVISION)
                .where(projectScope(projectId))
                .orderBy(TIMELINE_REVISION.REVISION_NUMBER.desc())
                .limit(1)
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    public List<RevisionRow> listByProject(String projectId, int limit) {
        return listByProject(projectId, null, limit);
    }

    public List<RevisionRow> listByProject(String projectId, String editSessionId, int limit) {
        return listByProject(projectId, editSessionId, null, null, limit);
    }

    public List<RevisionRow> listByProject(
            String projectId, String editSessionId, String authorUserId, String source, int limit) {
        var query = dsl.select()
                .from(TIMELINE_REVISION)
                .where(projectScope(projectId));
        if (editSessionId != null && !editSessionId.isBlank()) {
            query = query.and(TIMELINE_REVISION.EDIT_SESSION_ID.eq(editSessionId));
        }
        if (authorUserId != null && !authorUserId.isBlank()) {
            query = query.and(TIMELINE_REVISION.AUTHOR_USER_ID.eq(authorUserId));
        }
        if (source != null && !source.isBlank()) {
            query = query.and(TIMELINE_REVISION.SOURCE.eq(source));
        }
        return query
                .orderBy(TIMELINE_REVISION.REVISION_NUMBER.desc())
                .limit(Math.max(1, Math.min(limit, 200)))
                .fetch()
                .map(TimelineRevisionRepository::map);
    }

    public boolean updateAnnotation(String revisionId, String projectId, String message, String labelsJson) {
        int updated = dsl.update(TIMELINE_REVISION)
                .set(TIMELINE_REVISION.MESSAGE, message)
                .set(TIMELINE_REVISION.LABELS_JSON, labelsJson)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .and(projectScope(projectId))
                .execute();
        return updated > 0;
    }

    public List<String> listDistinctSources(String projectId) {
        return dsl.selectDistinct(TIMELINE_REVISION.SOURCE)
                .from(TIMELINE_REVISION)
                .where(projectScope(projectId))
                .orderBy(TIMELINE_REVISION.SOURCE.asc())
                .fetch(TIMELINE_REVISION.SOURCE);
    }

    public List<AuthorFacetRow> listAuthorFacets(String projectId, int limit) {
        int cap = Math.max(1, Math.min(limit, 50));
        var revisionCountField = DSL.count().as("revision_count");
        return dsl.select(
                        TIMELINE_REVISION.AUTHOR_USER_ID,
                        revisionCountField)
                .from(TIMELINE_REVISION)
                .where(projectScope(projectId))
                .and(TIMELINE_REVISION.AUTHOR_USER_ID.isNotNull())
                .groupBy(TIMELINE_REVISION.AUTHOR_USER_ID)
                .orderBy(revisionCountField.desc())
                .limit(cap)
                .fetch()
                .map(r -> new AuthorFacetRow(
                        r.get(TIMELINE_REVISION.AUTHOR_USER_ID),
                        r.get(revisionCountField, Integer.class)));
    }

    public List<EditSessionRow> listEditSessions(String projectId, int limit) {
        int cap = Math.max(1, Math.min(limit, 100));
        var revisionCountField = DSL.count().as("revision_count");
        var maxCreatedAtField = DSL.max(TIMELINE_REVISION.CREATED_AT).as("last_at");
        return dsl.select(
                        TIMELINE_REVISION.EDIT_SESSION_ID,
                        maxCreatedAtField,
                        revisionCountField)
                .from(TIMELINE_REVISION)
                .where(projectScope(projectId))
                .and(TIMELINE_REVISION.EDIT_SESSION_ID.isNotNull())
                .groupBy(TIMELINE_REVISION.EDIT_SESSION_ID)
                .orderBy(maxCreatedAtField.desc())
                .limit(cap)
                .fetch()
                .map(r -> new EditSessionRow(
                        r.get(TIMELINE_REVISION.EDIT_SESSION_ID),
                        toOffsetDateTime(r.get(maxCreatedAtField)),
                        r.get(revisionCountField, Integer.class)));
    }

    private static Condition tenantCondition() {
        return TIMELINE_REVISION.TENANT_ID.eq(TenantGuard.requireTenantId());
    }

    private static Condition projectScope(String projectId) {
        return TIMELINE_REVISION.PROJECT_ID.eq(projectId).and(tenantCondition());
    }

    // ---- CFRH-I2: explicit ownership-scoped read predicates (no ambient TenantGuard) ----

    private static Condition ownedScope(String projectId, String tenantId) {
        return TIMELINE_REVISION.PROJECT_ID.eq(projectId)
                .and(TIMELINE_REVISION.TENANT_ID.eq(tenantId));
    }

    public Optional<RevisionRow> findOwnedById(String revisionId, String projectId, String tenantId) {
        Record row = dsl.select()
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .and(ownedScope(projectId, tenantId))
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    public Optional<RevisionRow> findOwnedHead(String projectId, String tenantId) {
        Record row = dsl.select()
                .from(TIMELINE_REVISION)
                .where(ownedScope(projectId, tenantId))
                .orderBy(TIMELINE_REVISION.REVISION_NUMBER.desc())
                .limit(1)
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    public List<RevisionRow> listOwnedByProject(
            String projectId, String tenantId, String editSessionId, String authorUserId, String source, int limit) {
        var query = dsl.select()
                .from(TIMELINE_REVISION)
                .where(ownedScope(projectId, tenantId));
        if (editSessionId != null && !editSessionId.isBlank()) {
            query = query.and(TIMELINE_REVISION.EDIT_SESSION_ID.eq(editSessionId));
        }
        if (authorUserId != null && !authorUserId.isBlank()) {
            query = query.and(TIMELINE_REVISION.AUTHOR_USER_ID.eq(authorUserId));
        }
        if (source != null && !source.isBlank()) {
            query = query.and(TIMELINE_REVISION.SOURCE.eq(source));
        }
        return query
                .orderBy(TIMELINE_REVISION.REVISION_NUMBER.desc())
                .limit(Math.max(1, Math.min(limit, 200)))
                .fetch()
                .map(TimelineRevisionRepository::map);
    }

    public boolean updateOwnedAnnotation(
            String revisionId, String projectId, String tenantId, String message, String labelsJson) {
        int updated = dsl.update(TIMELINE_REVISION)
                .set(TIMELINE_REVISION.MESSAGE, message)
                .set(TIMELINE_REVISION.LABELS_JSON, labelsJson)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .and(ownedScope(projectId, tenantId))
                .execute();
        return updated > 0;
    }

    public List<String> listOwnedDistinctSources(String projectId, String tenantId) {
        return dsl.selectDistinct(TIMELINE_REVISION.SOURCE)
                .from(TIMELINE_REVISION)
                .where(ownedScope(projectId, tenantId))
                .orderBy(TIMELINE_REVISION.SOURCE.asc())
                .fetch(TIMELINE_REVISION.SOURCE);
    }

    public List<AuthorFacetRow> listOwnedAuthorFacets(String projectId, String tenantId, int limit) {
        int cap = Math.max(1, Math.min(limit, 50));
        var revisionCountField = DSL.count().as("revision_count");
        return dsl.select(
                        TIMELINE_REVISION.AUTHOR_USER_ID,
                        revisionCountField)
                .from(TIMELINE_REVISION)
                .where(ownedScope(projectId, tenantId))
                .and(TIMELINE_REVISION.AUTHOR_USER_ID.isNotNull())
                .groupBy(TIMELINE_REVISION.AUTHOR_USER_ID)
                .orderBy(revisionCountField.desc())
                .limit(cap)
                .fetch()
                .map(r -> new AuthorFacetRow(
                        r.get(TIMELINE_REVISION.AUTHOR_USER_ID),
                        r.get(revisionCountField, Integer.class)));
    }

    public List<EditSessionRow> listOwnedEditSessions(String projectId, String tenantId, int limit) {
        int cap = Math.max(1, Math.min(limit, 100));
        var revisionCountField = DSL.count().as("revision_count");
        var maxCreatedAtField = DSL.max(TIMELINE_REVISION.CREATED_AT).as("last_at");
        return dsl.select(
                        TIMELINE_REVISION.EDIT_SESSION_ID,
                        maxCreatedAtField,
                        revisionCountField)
                .from(TIMELINE_REVISION)
                .where(ownedScope(projectId, tenantId))
                .and(TIMELINE_REVISION.EDIT_SESSION_ID.isNotNull())
                .groupBy(TIMELINE_REVISION.EDIT_SESSION_ID)
                .orderBy(maxCreatedAtField.desc())
                .limit(cap)
                .fetch()
                .map(r -> new EditSessionRow(
                        r.get(TIMELINE_REVISION.EDIT_SESSION_ID),
                        toOffsetDateTime(r.get(maxCreatedAtField)),
                        r.get(revisionCountField, Integer.class)));
    }

    public int nextRevisionNumber(String projectId) {
        return nextRevisionNumberTx(dsl, projectId);
    }

    /**
     * FINAL_CLOSURE_F1: transaction-aware revision-number allocation — reads
     * the max through the caller's DSLContext so the number allocation and the
     * revision insert share one physical transaction (no gap between
     * allocation and insert).
     */
    public int nextRevisionNumberTx(org.jooq.DSLContext tx, String projectId) {
        Integer max = tx.select(TIMELINE_REVISION.REVISION_NUMBER)
                .from(TIMELINE_REVISION)
                .where(projectScope(projectId))
                .orderBy(TIMELINE_REVISION.REVISION_NUMBER.desc())
                .limit(1)
                .fetchOne(TIMELINE_REVISION.REVISION_NUMBER);
        return max == null ? 1 : max + 1;
    }

    public void insert(RevisionRow row) {
        insertTx(dsl, row);
    }

    /**
     * FINAL_CLOSURE_F1 (CHECKPOINT_A post-Round-5): transaction-aware insert —
     * writes through the caller's DSLContext so the revision row joins the SAME
     * physical DB transaction as snapshot/pin/head writes in the persistent
     * merge path. Never assumed to participate in a caller transaction via the
     * root DSLContext.
     */
    public void insertTx(org.jooq.DSLContext tx, RevisionRow row) {
        tx.insertInto(TIMELINE_REVISION)
                .columns(
                        TIMELINE_REVISION.ID,
                        TIMELINE_REVISION.PROJECT_ID,
                        TIMELINE_REVISION.TENANT_ID,
                        TIMELINE_REVISION.PARENT_REVISION_ID,
                        TIMELINE_REVISION.REVISION_NUMBER,
                        TIMELINE_REVISION.SNAPSHOT_ID,
                        TIMELINE_REVISION.INTERNAL_REVISION,
                        TIMELINE_REVISION.CONTENT_HASH,
                        TIMELINE_REVISION.SCHEMA_VERSION,
                        TIMELINE_REVISION.SOURCE,
                        TIMELINE_REVISION.AUTHOR_USER_ID,
                        TIMELINE_REVISION.EDIT_SESSION_ID,
                        TIMELINE_REVISION.MESSAGE,
                        TIMELINE_REVISION.CHANGE_SUMMARY_JSON,
                        TIMELINE_REVISION.PATCH_OPS_JSON,
                        TIMELINE_REVISION.LABELS_JSON,
                        TIMELINE_REVISION.IS_MERGE,
                        TIMELINE_REVISION.MERGE_PARENT_REVISION_IDS,
                        TIMELINE_REVISION.MERGE_BASE_REVISION_ID,
                        TIMELINE_REVISION.CREATED_AT)
                .values(
                        row.id(),
                        row.projectId(),
                        row.tenantId(),
                        row.parentRevisionId(),
                        row.revisionNumber(),
                        row.snapshotId(),
                        row.internalRevision(),
                        row.contentHash(),
                        row.schemaVersion(),
                        row.source(),
                        row.authorUserId(),
                        row.editSessionId(),
                        row.message(),
                        row.changeSummaryJson(),
                        row.patchOpsJson(),
                        row.labelsJson(),
                        row.isMerge(),
                        row.mergeParentRevisionIds(),
                        row.mergeBaseRevisionId(),
                        row.createdAt().toLocalDateTime())
                .execute();
    }

    private static RevisionRow map(Record row) {
        return new RevisionRow(
                row.get(TIMELINE_REVISION.ID),
                row.get(TIMELINE_REVISION.PROJECT_ID),
                row.get(TIMELINE_REVISION.TENANT_ID),
                row.get(TIMELINE_REVISION.PARENT_REVISION_ID),
                row.get(TIMELINE_REVISION.REVISION_NUMBER),
                row.get(TIMELINE_REVISION.SNAPSHOT_ID),
                row.get(TIMELINE_REVISION.INTERNAL_REVISION),
                row.get(TIMELINE_REVISION.CONTENT_HASH),
                row.get(TIMELINE_REVISION.SCHEMA_VERSION),
                row.get(TIMELINE_REVISION.SOURCE),
                row.get(TIMELINE_REVISION.AUTHOR_USER_ID),
                row.get(TIMELINE_REVISION.EDIT_SESSION_ID),
                row.get(TIMELINE_REVISION.MESSAGE),
                row.get(TIMELINE_REVISION.CHANGE_SUMMARY_JSON),
                row.get(TIMELINE_REVISION.PATCH_OPS_JSON),
                row.get(TIMELINE_REVISION.LABELS_JSON),
                Boolean.TRUE.equals(row.get("is_merge", Boolean.class)),
                row.get(TIMELINE_REVISION.MERGE_PARENT_REVISION_IDS),
                row.get(TIMELINE_REVISION.MERGE_BASE_REVISION_ID),
                toOffsetDateTime(row.get(TIMELINE_REVISION.CREATED_AT)));
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return LocalDateTime.now().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    public record RevisionRow(
            String id,
            String projectId,
            String tenantId,
            String parentRevisionId,
            int revisionNumber,
            String snapshotId,
            int internalRevision,
            String contentHash,
            String schemaVersion,
            String source,
            String authorUserId,
            String editSessionId,
            String message,
            String changeSummaryJson,
            String patchOpsJson,
            String labelsJson,
            boolean isMerge,
            String mergeParentRevisionIds,
            String mergeBaseRevisionId,
            OffsetDateTime createdAt) {}

    public record AuthorFacetRow(String authorUserId, int revisionCount) {}

    public record EditSessionRow(String editSessionId, OffsetDateTime lastAt, int revisionCount) {}
}
