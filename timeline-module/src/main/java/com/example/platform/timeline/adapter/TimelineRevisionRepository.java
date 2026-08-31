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
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevisionRef.TIMELINE_REVISION_REF;
import org.jooq.impl.DSL;


@Repository
public class TimelineRevisionRepository {

    private final DSLContext dsl;

    public TimelineRevisionRepository(DSLContext dsl) {
        this.dsl = dsl;
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
                .join(TIMELINE_REVISION_REF)
                .on(TIMELINE_REVISION_REF.HEAD_REVISION_ID.eq(TIMELINE_REVISION.ID))
                .and(TIMELINE_REVISION_REF.PROJECT_ID.eq(TIMELINE_REVISION.PROJECT_ID))
                .and(TIMELINE_REVISION_REF.TENANT_ID.eq(TIMELINE_REVISION.TENANT_ID))
                .where(ownedScope(projectId, tenantId))
                .and(TIMELINE_REVISION_REF.REF_ID.eq(
                        com.example.platform.timeline.revisioncommand.RevisionRef.MAIN_REF))
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
