package com.example.platform.render.app.timeline;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.shared.web.TenantGuard;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class TimelineReviewRepository {

    private final DSLContext dsl;

    public TimelineReviewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insertReview(String id, String projectId, String tenantId, String revisionId,
                               String authorUserId, String title, String description,
                               String status, OffsetDateTime now) {
        dsl.insertInto(table("timeline_review"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("revision_id"), field("target_type"), field("author_user_id"),
                        field("title"), field("description"), field("status"),
                        field("created_at"), field("updated_at"))
                .values(id, projectId, tenantId, revisionId, "TIMELINE", authorUserId,
                        title, description, status, now, now)
                .execute();
    }

    public void setTargetType(String reviewId, String targetType) {
        dsl.update(table("timeline_review"))
                .set(field("target_type"), targetType)
                .where(field("id").eq(reviewId))
                .execute();
    }

    public Optional<ReviewRow> findByTargetId(String targetId) {
        Record row = dsl.select().from(table("timeline_review"))
                .where(field("revision_id").eq(targetId))
                .orderBy(field("created_at").desc())
                .limit(1)
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(mapReview(row));
    }

    public void updateReviewStatus(String reviewId, String status) {
        dsl.update(table("timeline_review"))
                .set(field("status"), status)
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(reviewId))
                .execute();
    }

    public Optional<ReviewRow> findById(String reviewId) {
        Record row = dsl.select().from(table("timeline_review"))
                .where(field("id").eq(reviewId))
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(mapReview(row));
    }

    public List<ReviewRow> listByProject(String projectId, int limit) {
        return dsl.select().from(table("timeline_review"))
                .where(projectScope(projectId))
                .orderBy(field("created_at").desc())
                .limit(Math.min(limit, 100))
                .fetch().map(TimelineReviewRepository::mapReview);
    }

    public void insertComment(String id, String reviewId, String threadId, String revisionId,
                                String entityRef, String authorUserId, String content,
                                OffsetDateTime now) {
        dsl.insertInto(table("timeline_comment"))
                .columns(field("id"), field("review_id"), field("thread_id"),
                        field("revision_id"), field("entity_ref"),
                        field("author_user_id"), field("content"), field("created_at"))
                .values(id, reviewId, threadId, revisionId, entityRef,
                        authorUserId, content, now)
                .execute();
    }

    public void insertThread(String id, String reviewId, String entityRef,
                               String diffId, String status, OffsetDateTime now) {
        dsl.insertInto(table("review_thread"))
                .columns(field("id"), field("review_id"), field("entity_ref"),
                        field("diff_id"), field("status"), field("created_at"))
                .values(id, reviewId, entityRef, diffId, status, now)
                .execute();
    }

    public void updateThreadStatus(String threadId, String status) {
        dsl.update(table("review_thread"))
                .set(field("status"), status)
                .where(field("id").eq(threadId))
                .execute();
    }

    public void insertDecision(String id, String reviewId, String reviewerUserId,
                                 String decision, OffsetDateTime now) {
        dsl.insertInto(table("review_decision"))
                .columns(field("id"), field("review_id"), field("reviewer_user_id"),
                        field("decision"), field("created_at"))
                .values(id, reviewId, reviewerUserId, decision, now)
                .execute();
    }

    public List<DecisionRow> listDecisionsByReview(String reviewId) {
        return dsl.select().from(table("review_decision"))
                .where(field("review_id").eq(reviewId))
                .orderBy(field("created_at").asc())
                .fetch().map(TimelineReviewRepository::mapDecision);
    }

    public List<CommentRow> listCommentsByReview(String reviewId) {
        return dsl.select().from(table("timeline_comment"))
                .where(field("review_id").eq(reviewId))
                .orderBy(field("created_at").asc())
                .fetch().map(TimelineReviewRepository::mapComment);
    }

    public List<ThreadRow> listThreadsByReview(String reviewId) {
        return dsl.select().from(table("review_thread"))
                .where(field("review_id").eq(reviewId))
                .fetch().map(TimelineReviewRepository::mapThread);
    }

    private static Condition projectScope(String projectId) {
        return field("project_id").eq(projectId)
                .and(field("tenant_id").eq(TenantGuard.requireTenantId()));
    }

    private static ReviewRow mapReview(Record r) {
        return new ReviewRow(
                r.get(field("id", String.class)),
                r.get(field("project_id", String.class)),
                r.get(field("tenant_id", String.class)),
                r.get(field("revision_id", String.class)),
                r.get(field("author_user_id", String.class)),
                r.get(field("title", String.class)),
                r.get(field("description", String.class)),
                r.get(field("status", String.class)),
                r.get(field("created_at", OffsetDateTime.class)),
                r.get(field("updated_at", OffsetDateTime.class)));
    }

    private static CommentRow mapComment(Record r) {
        return new CommentRow(
                r.get(field("id", String.class)),
                r.get(field("review_id", String.class)),
                r.get(field("thread_id", String.class)),
                r.get(field("revision_id", String.class)),
                r.get(field("entity_ref", String.class)),
                r.get(field("author_user_id", String.class)),
                r.get(field("content", String.class)),
                r.get(field("created_at", OffsetDateTime.class)));
    }

    private static ThreadRow mapThread(Record r) {
        return new ThreadRow(
                r.get(field("id", String.class)),
                r.get(field("review_id", String.class)),
                r.get(field("entity_ref", String.class)),
                r.get(field("diff_id", String.class)),
                r.get(field("status", String.class)),
                r.get(field("created_at", OffsetDateTime.class)));
    }

    private static DecisionRow mapDecision(Record r) {
        return new DecisionRow(
                r.get(field("id", String.class)),
                r.get(field("review_id", String.class)),
                r.get(field("reviewer_user_id", String.class)),
                r.get(field("decision", String.class)),
                r.get(field("created_at", OffsetDateTime.class)));
    }

    public record ReviewRow(String id, String projectId, String tenantId, String revisionId,
                             String authorUserId, String title, String description,
                             String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record CommentRow(String id, String reviewId, String threadId, String revisionId,
                               String entityRef, String authorUserId, String content,
                               OffsetDateTime createdAt) {}

    public record ThreadRow(String id, String reviewId, String entityRef, String diffId,
                              String status, OffsetDateTime createdAt) {}

    public record DecisionRow(String id, String reviewId, String reviewerUserId,
                                String decision, OffsetDateTime createdAt) {}
}
