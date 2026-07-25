package com.example.platform.render.app.timeline;

import com.example.platform.shared.web.TenantGuard;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.ReviewDecision.REVIEW_DECISION;
import static com.example.platform.typedschema.jooq.generated.tables.ReviewThread.REVIEW_THREAD;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineComment.TIMELINE_COMMENT;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineReview.TIMELINE_REVIEW;


@Repository
public class TimelineReviewRepository {

    private final DSLContext dsl;

    public TimelineReviewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insertReview(String id, String projectId, String tenantId, String revisionId,
                               String authorUserId, String title, String description,
                               String status, OffsetDateTime now) {
        dsl.insertInto(TIMELINE_REVIEW)
                .columns(TIMELINE_REVIEW.ID, TIMELINE_REVIEW.PROJECT_ID, TIMELINE_REVIEW.TENANT_ID,
                        TIMELINE_REVIEW.REVISION_ID, TIMELINE_REVIEW.TARGET_TYPE, TIMELINE_REVIEW.AUTHOR_USER_ID,
                        TIMELINE_REVIEW.TITLE, TIMELINE_REVIEW.DESCRIPTION, TIMELINE_REVIEW.STATUS,
                        TIMELINE_REVIEW.CREATED_AT, TIMELINE_REVIEW.UPDATED_AT)
                .values(id, projectId, tenantId, revisionId, "TIMELINE", authorUserId,
                        title, description, status, now.toLocalDateTime(), now.toLocalDateTime())
                .execute();
    }

    public void setTargetType(String reviewId, String targetType) {
        dsl.update(TIMELINE_REVIEW)
                .set(TIMELINE_REVIEW.TARGET_TYPE, targetType)
                .where(TIMELINE_REVIEW.ID.eq(reviewId))
                .execute();
    }

    public Optional<ReviewRow> findByTargetId(String targetId) {
        Record row = dsl.select().from(TIMELINE_REVIEW)
                .where(TIMELINE_REVIEW.REVISION_ID.eq(targetId))
                .orderBy(TIMELINE_REVIEW.CREATED_AT.desc())
                .limit(1)
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(mapReview(row));
    }

    public void updateReviewStatus(String reviewId, String status) {
        dsl.update(TIMELINE_REVIEW)
                .set(TIMELINE_REVIEW.STATUS, status)
                .set(TIMELINE_REVIEW.UPDATED_AT, LocalDateTime.now())
                .where(TIMELINE_REVIEW.ID.eq(reviewId))
                .execute();
    }

    public Optional<ReviewRow> findById(String reviewId) {
        Record row = dsl.select().from(TIMELINE_REVIEW)
                .where(TIMELINE_REVIEW.ID.eq(reviewId))
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(mapReview(row));
    }

    public List<ReviewRow> listByProject(String projectId, int limit) {
        return dsl.select().from(TIMELINE_REVIEW)
                .where(projectScope(projectId))
                .orderBy(TIMELINE_REVIEW.CREATED_AT.desc())
                .limit(Math.min(limit, 100))
                .fetch().map(TimelineReviewRepository::mapReview);
    }

    public void insertComment(String id, String reviewId, String threadId, String revisionId,
                                String entityRef, String authorUserId, String content,
                                OffsetDateTime now) {
        dsl.insertInto(TIMELINE_COMMENT)
                .columns(TIMELINE_COMMENT.ID, TIMELINE_COMMENT.REVIEW_ID, TIMELINE_COMMENT.THREAD_ID,
                        TIMELINE_COMMENT.REVISION_ID, TIMELINE_COMMENT.ENTITY_REF,
                        TIMELINE_COMMENT.AUTHOR_USER_ID, TIMELINE_COMMENT.CONTENT, TIMELINE_COMMENT.CREATED_AT)
                .values(id, reviewId, threadId, revisionId, entityRef,
                        authorUserId, content, now.toLocalDateTime())
                .execute();
    }

    public void insertThread(String id, String reviewId, String entityRef,
                               String diffId, String status, OffsetDateTime now) {
        dsl.insertInto(REVIEW_THREAD)
                .columns(REVIEW_THREAD.ID, REVIEW_THREAD.REVIEW_ID, REVIEW_THREAD.ENTITY_REF,
                        REVIEW_THREAD.DIFF_ID, REVIEW_THREAD.STATUS, REVIEW_THREAD.CREATED_AT)
                .values(id, reviewId, entityRef, diffId, status, now.toLocalDateTime())
                .execute();
    }

    public void updateThreadStatus(String threadId, String status) {
        dsl.update(REVIEW_THREAD)
                .set(REVIEW_THREAD.STATUS, status)
                .where(REVIEW_THREAD.ID.eq(threadId))
                .execute();
    }

    public void insertDecision(String id, String reviewId, String reviewerUserId,
                                 String decision, OffsetDateTime now) {
        dsl.insertInto(REVIEW_DECISION)
                .columns(REVIEW_DECISION.ID, REVIEW_DECISION.REVIEW_ID, REVIEW_DECISION.REVIEWER_USER_ID,
                        REVIEW_DECISION.DECISION, REVIEW_DECISION.CREATED_AT)
                .values(id, reviewId, reviewerUserId, decision, now.toLocalDateTime())
                .execute();
    }

    public List<DecisionRow> listDecisionsByReview(String reviewId) {
        return dsl.select().from(REVIEW_DECISION)
                .where(REVIEW_DECISION.REVIEW_ID.eq(reviewId))
                .orderBy(REVIEW_DECISION.CREATED_AT.asc())
                .fetch().map(TimelineReviewRepository::mapDecision);
    }

    public List<CommentRow> listCommentsByReview(String reviewId) {
        return dsl.select().from(TIMELINE_COMMENT)
                .where(TIMELINE_COMMENT.REVIEW_ID.eq(reviewId))
                .orderBy(TIMELINE_COMMENT.CREATED_AT.asc())
                .fetch().map(TimelineReviewRepository::mapComment);
    }

    public List<ThreadRow> listThreadsByReview(String reviewId) {
        return dsl.select().from(REVIEW_THREAD)
                .where(REVIEW_THREAD.REVIEW_ID.eq(reviewId))
                .fetch().map(TimelineReviewRepository::mapThread);
    }

    private static Condition projectScope(String projectId) {
        return TIMELINE_REVIEW.PROJECT_ID.eq(projectId)
                .and(TIMELINE_REVIEW.TENANT_ID.eq(TenantGuard.requireTenantId()));
    }

    private static ReviewRow mapReview(Record r) {
        return new ReviewRow(
                r.get(TIMELINE_REVIEW.ID),
                r.get(TIMELINE_REVIEW.PROJECT_ID),
                r.get(TIMELINE_REVIEW.TENANT_ID),
                r.get(TIMELINE_REVIEW.REVISION_ID),
                r.get(TIMELINE_REVIEW.AUTHOR_USER_ID),
                r.get(TIMELINE_REVIEW.TITLE),
                r.get(TIMELINE_REVIEW.DESCRIPTION),
                r.get(TIMELINE_REVIEW.STATUS),
                r.get(TIMELINE_REVIEW.CREATED_AT).atOffset(java.time.ZoneOffset.UTC),
                r.get(TIMELINE_REVIEW.UPDATED_AT).atOffset(java.time.ZoneOffset.UTC));
    }

    private static CommentRow mapComment(Record r) {
        return new CommentRow(
                r.get(TIMELINE_COMMENT.ID),
                r.get(TIMELINE_COMMENT.REVIEW_ID),
                r.get(TIMELINE_COMMENT.THREAD_ID),
                r.get(TIMELINE_COMMENT.REVISION_ID),
                r.get(TIMELINE_COMMENT.ENTITY_REF),
                r.get(TIMELINE_COMMENT.AUTHOR_USER_ID),
                r.get(TIMELINE_COMMENT.CONTENT),
                r.get(TIMELINE_COMMENT.CREATED_AT).atOffset(java.time.ZoneOffset.UTC));
    }

    private static ThreadRow mapThread(Record r) {
        return new ThreadRow(
                r.get(REVIEW_THREAD.ID),
                r.get(REVIEW_THREAD.REVIEW_ID),
                r.get(REVIEW_THREAD.ENTITY_REF),
                r.get(REVIEW_THREAD.DIFF_ID),
                r.get(REVIEW_THREAD.STATUS),
                r.get(REVIEW_THREAD.CREATED_AT).atOffset(java.time.ZoneOffset.UTC));
    }

    private static DecisionRow mapDecision(Record r) {
        return new DecisionRow(
                r.get(REVIEW_DECISION.ID),
                r.get(REVIEW_DECISION.REVIEW_ID),
                r.get(REVIEW_DECISION.REVIEWER_USER_ID),
                r.get(REVIEW_DECISION.DECISION),
                r.get(REVIEW_DECISION.CREATED_AT).atOffset(java.time.ZoneOffset.UTC));
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
