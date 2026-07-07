package com.example.platform.render.app.timeline;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.example.platform.shared.web.TenantGuard;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class TimelineRevisionRepository {

    private final DSLContext dsl;

    public TimelineRevisionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<RevisionRow> findById(String revisionId) {
        Record row = dsl.select()
                .from(table("timeline_revision"))
                .where(field("id").eq(revisionId))
                .and(tenantCondition())
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    public Optional<RevisionRow> findHeadByProject(String projectId) {
        Record row = dsl.select()
                .from(table("timeline_revision"))
                .where(projectScope(projectId))
                .orderBy(field("revision_number").desc())
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
                .from(table("timeline_revision"))
                .where(projectScope(projectId));
        if (editSessionId != null && !editSessionId.isBlank()) {
            query = query.and(field("edit_session_id").eq(editSessionId));
        }
        if (authorUserId != null && !authorUserId.isBlank()) {
            query = query.and(field("author_user_id").eq(authorUserId));
        }
        if (source != null && !source.isBlank()) {
            query = query.and(field("source").eq(source));
        }
        return query
                .orderBy(field("revision_number").desc())
                .limit(Math.max(1, Math.min(limit, 200)))
                .fetch()
                .map(TimelineRevisionRepository::map);
    }

    public boolean updateAnnotation(String revisionId, String projectId, String message, String labelsJson) {
        int updated = dsl.update(table("timeline_revision"))
                .set(field("message"), message)
                .set(field("labels_json"), labelsJson)
                .where(field("id").eq(revisionId))
                .and(projectScope(projectId))
                .execute();
        return updated > 0;
    }

    public List<String> listDistinctSources(String projectId) {
        return dsl.selectDistinct(field("source", String.class))
                .from(table("timeline_revision"))
                .where(projectScope(projectId))
                .orderBy(field("source").asc())
                .fetch(field("source", String.class));
    }

    public List<AuthorFacetRow> listAuthorFacets(String projectId, int limit) {
        int cap = Math.max(1, Math.min(limit, 50));
        return dsl.select(
                        field("author_user_id", String.class),
                        org.jooq.impl.DSL.count().as("revision_count"))
                .from(table("timeline_revision"))
                .where(projectScope(projectId))
                .and(field("author_user_id").isNotNull())
                .groupBy(field("author_user_id"))
                .orderBy(org.jooq.impl.DSL.count().desc())
                .limit(cap)
                .fetch()
                .map(r -> new AuthorFacetRow(
                        r.get(field("author_user_id", String.class)),
                        r.get(org.jooq.impl.DSL.field("revision_count", Integer.class))));
    }

    public List<EditSessionRow> listEditSessions(String projectId, int limit) {
        int cap = Math.max(1, Math.min(limit, 100));
        return dsl.select(
                        field("edit_session_id", String.class),
                        org.jooq.impl.DSL.max(field("created_at")).as("last_at"),
                        org.jooq.impl.DSL.count().as("revision_count"))
                .from(table("timeline_revision"))
                .where(projectScope(projectId))
                .and(field("edit_session_id").isNotNull())
                .groupBy(field("edit_session_id"))
                .orderBy(org.jooq.impl.DSL.max(field("created_at")).desc())
                .limit(cap)
                .fetch()
                .map(r -> new EditSessionRow(
                        r.get(field("edit_session_id", String.class)),
                        toOffsetDateTime(r.get("last_at")),
                        r.get(org.jooq.impl.DSL.field("revision_count", Integer.class))));
    }

    private static Condition tenantCondition() {
        return field("tenant_id").eq(TenantGuard.requireTenantId());
    }

    private static Condition projectScope(String projectId) {
        return field("project_id").eq(projectId).and(tenantCondition());
    }

    public int nextRevisionNumber(String projectId) {
        Integer max = dsl.select(field("revision_number", Integer.class))
                .from(table("timeline_revision"))
                .where(projectScope(projectId))
                .orderBy(field("revision_number").desc())
                .limit(1)
                .fetchOne(field("revision_number", Integer.class));
        return max == null ? 1 : max + 1;
    }

    public void insert(RevisionRow row) {
        dsl.insertInto(table("timeline_revision"))
                .columns(
                        field("id"),
                        field("project_id"),
                        field("tenant_id"),
                        field("parent_revision_id"),
                        field("revision_number"),
                        field("snapshot_id"),
                        field("internal_revision"),
                        field("content_hash"),
                        field("schema_version"),
                        field("source"),
                        field("author_user_id"),
                        field("edit_session_id"),
                        field("message"),
                        field("change_summary_json"),
                        field("patch_ops_json"),
                        field("labels_json"),
                        field("is_merge"),
                        field("merge_parent_revision_ids"),
                        field("merge_base_revision_id"),
                        field("created_at"))
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
                        row.createdAt())
                .execute();
    }

    private static RevisionRow map(Record row) {
        return new RevisionRow(
                row.get(field("id", String.class)),
                row.get(field("project_id", String.class)),
                row.get(field("tenant_id", String.class)),
                row.get(field("parent_revision_id", String.class)),
                row.get(field("revision_number", Integer.class)),
                row.get(field("snapshot_id", String.class)),
                row.get(field("internal_revision", Integer.class)),
                row.get(field("content_hash", String.class)),
                row.get(field("schema_version", String.class)),
                row.get(field("source", String.class)),
                row.get(field("author_user_id", String.class)),
                row.get(field("edit_session_id", String.class)),
                row.get(field("message", String.class)),
                row.get(field("change_summary_json", String.class)),
                row.get(field("patch_ops_json", String.class)),
                row.get(field("labels_json", String.class)),
                Boolean.TRUE.equals(row.get("is_merge", Boolean.class)),
                row.get(field("merge_parent_revision_ids", String.class)),
                row.get(field("merge_base_revision_id", String.class)),
                toOffsetDateTime(row.get(field("created_at"))));
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return OffsetDateTime.now();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
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
