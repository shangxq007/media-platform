package com.example.platform.timeline.infrastructure.review;
import com.example.platform.timeline.api.review.ReviewFrameAnchor;
import com.example.platform.timeline.api.review.ReviewRecords.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.timeline.tables.ReviewDecision.REVIEW_DECISION;
import static com.example.platform.typedschema.jooq.generated.timeline.tables.ReviewThread.REVIEW_THREAD;
import static com.example.platform.typedschema.jooq.generated.timeline.tables.TimelineComment.TIMELINE_COMMENT;
import static com.example.platform.typedschema.jooq.generated.timeline.tables.TimelineReview.TIMELINE_REVIEW;


@Repository
public class TimelineReviewRepository {

    // entity_ref already holds tagged review targets. Frame targets are disjoint
    // from the legacy EntityRef grammar and are never projected as entity IDs.
    private static final String FRAME_PREFIX = "FRAME.v1|";

    private final DSLContext dsl;

    public TimelineReviewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insertReview(String id, String projectId, String tenantId, String revisionId,
                             String authorUserId, String title, String description, String status, OffsetDateTime now) {
        insertReview(id, projectId, tenantId, revisionId, authorUserId, title, description, status, now, false);
    }

    public boolean insertFrameReviewIfAbsent(String id, String projectId, String tenantId, String revisionId,
                                             String authorUserId, OffsetDateTime now) {
        return insertReview(id, projectId, tenantId, revisionId, authorUserId, "Frame comments", null, "OPEN", now, true);
    }

    private boolean insertReview(String id, String projectId, String tenantId, String revisionId, String authorUserId,
                                 String title, String description, String status, OffsetDateTime now, boolean ifAbsent) {
        var insert = dsl.insertInto(TIMELINE_REVIEW)
                .columns(TIMELINE_REVIEW.ID, TIMELINE_REVIEW.PROJECT_ID, TIMELINE_REVIEW.TENANT_ID,
                        TIMELINE_REVIEW.REVISION_ID, TIMELINE_REVIEW.TARGET_TYPE, TIMELINE_REVIEW.AUTHOR_USER_ID,
                        TIMELINE_REVIEW.TITLE, TIMELINE_REVIEW.DESCRIPTION, TIMELINE_REVIEW.STATUS,
                        TIMELINE_REVIEW.CREATED_AT, TIMELINE_REVIEW.UPDATED_AT)
                .values(id, projectId, tenantId, revisionId, "TIMELINE", authorUserId,
                        title, description, status, now.toLocalDateTime(), now.toLocalDateTime());
        return (ifAbsent ? insert.onConflictDoNothing() : insert).execute() == 1;
    }

    public String targetType(String reviewId) {
        return dsl.select(TIMELINE_REVIEW.TARGET_TYPE).from(TIMELINE_REVIEW).where(TIMELINE_REVIEW.ID.eq(reviewId))
            .and(TIMELINE_REVIEW.TENANT_ID.eq(com.example.platform.shared.web.TenantGuard.requireTenantId())).and(TIMELINE_REVIEW.TARGET_TYPE.eq("TIMELINE")).fetchOne(TIMELINE_REVIEW.TARGET_TYPE);
    }
    public void lock(String reviewId) {
        dsl.select(TIMELINE_REVIEW.ID).from(TIMELINE_REVIEW).where(TIMELINE_REVIEW.ID.eq(reviewId))
            .and(TIMELINE_REVIEW.TENANT_ID.eq(com.example.platform.shared.web.TenantGuard.requireTenantId())).and(TIMELINE_REVIEW.TARGET_TYPE.eq("TIMELINE")).forUpdate().fetchOne();
    }
    public Optional<ReviewRow> findByTargetId(String targetId) {
        Record row = dsl.select().from(TIMELINE_REVIEW)
                .where(TIMELINE_REVIEW.REVISION_ID.eq(targetId)).and(TIMELINE_REVIEW.TENANT_ID.eq(com.example.platform.shared.web.TenantGuard.requireTenantId())).and(TIMELINE_REVIEW.TARGET_TYPE.eq("TIMELINE"))
                .orderBy(TIMELINE_REVIEW.CREATED_AT.desc())
                .limit(1)
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(mapReview(row));
    }

    public void updateReviewStatus(String reviewId, String status) {
        dsl.update(TIMELINE_REVIEW)
                .set(TIMELINE_REVIEW.STATUS, status)
                .set(TIMELINE_REVIEW.UPDATED_AT, LocalDateTime.now())
                .where(TIMELINE_REVIEW.ID.eq(reviewId)).and(TIMELINE_REVIEW.TENANT_ID.eq(com.example.platform.shared.web.TenantGuard.requireTenantId())).and(TIMELINE_REVIEW.TARGET_TYPE.eq("TIMELINE"))
                .execute();
    }

    public Optional<ReviewRow> findById(String reviewId) {
        Record row = dsl.select().from(TIMELINE_REVIEW)
                .where(TIMELINE_REVIEW.ID.eq(reviewId)).and(TIMELINE_REVIEW.TENANT_ID.eq(com.example.platform.shared.web.TenantGuard.requireTenantId())).and(TIMELINE_REVIEW.TARGET_TYPE.eq("TIMELINE"))
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(mapReview(row));
    }

    public Optional<ReviewRow> findOwnedById(
            String reviewId, String projectId, String tenantId) {
        Record row = dsl.select().from(TIMELINE_REVIEW)
                .where(TIMELINE_REVIEW.ID.eq(reviewId)).and(TIMELINE_REVIEW.TENANT_ID.eq(com.example.platform.shared.web.TenantGuard.requireTenantId())).and(TIMELINE_REVIEW.TARGET_TYPE.eq("TIMELINE"))
                .and(TIMELINE_REVIEW.PROJECT_ID.eq(projectId))
                .and(TIMELINE_REVIEW.TENANT_ID.eq(tenantId)).and(TIMELINE_REVIEW.TARGET_TYPE.eq("TIMELINE"))
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(mapReview(row));
    }

    public List<ReviewRow> listOwnedByProject(String projectId, String tenantId, int limit) {
        return dsl.select().from(TIMELINE_REVIEW)
                .where(TIMELINE_REVIEW.PROJECT_ID.eq(projectId))
                .and(TIMELINE_REVIEW.TENANT_ID.eq(tenantId)).and(TIMELINE_REVIEW.TARGET_TYPE.eq("TIMELINE"))
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

    public boolean insertFrameComment(String id, String reviewId, String revisionId, ReviewFrameAnchor anchor,
                                      String authorUserId, String content, OffsetDateTime now) {
        String ref = FRAME_PREFIX + anchor.side() + "|" + anchor.branch() + "|" + anchor.position();
        return dsl.insertInto(TIMELINE_COMMENT)
                .columns(TIMELINE_COMMENT.ID, TIMELINE_COMMENT.REVIEW_ID, TIMELINE_COMMENT.REVISION_ID,
                        TIMELINE_COMMENT.ENTITY_REF, TIMELINE_COMMENT.AUTHOR_USER_ID,
                        TIMELINE_COMMENT.CONTENT, TIMELINE_COMMENT.CREATED_AT)
                .values(id, reviewId, revisionId, ref, authorUserId, content, now.toLocalDateTime())
                .onConflictDoNothing().execute() == 1;
    }

    public Optional<CommentRow> findOwnedComment(String projectId, String tenantId, String id) {
        return dsl.select(TIMELINE_COMMENT.fields()).from(TIMELINE_COMMENT).join(TIMELINE_REVIEW)
                .on(TIMELINE_COMMENT.REVIEW_ID.eq(TIMELINE_REVIEW.ID))
                .where(ownedReview(projectId, tenantId)).and(TIMELINE_COMMENT.ID.eq(id))
                .fetchOptional(TimelineReviewRepository::mapComment);
    }

    public List<CommentRow> listOwnedProjectComments(String projectId, String tenantId) {
        return dsl.select(TIMELINE_COMMENT.fields()).from(TIMELINE_COMMENT).join(TIMELINE_REVIEW)
                .on(TIMELINE_COMMENT.REVIEW_ID.eq(TIMELINE_REVIEW.ID))
                .where(ownedReview(projectId, tenantId))
                .orderBy(TIMELINE_COMMENT.CREATED_AT.desc(), TIMELINE_COMMENT.ID.desc())
                .fetch(TimelineReviewRepository::mapComment);
    }

    private static Condition ownedReview(String projectId, String tenantId) {
        return TIMELINE_REVIEW.PROJECT_ID.eq(projectId).and(TIMELINE_REVIEW.TENANT_ID.eq(tenantId))
                .and(TIMELINE_REVIEW.TARGET_TYPE.eq("TIMELINE"));
    }

    public void insertThread(String id, String reviewId, String entityRef,
                               String diffId, String status, OffsetDateTime now) {
        dsl.insertInto(REVIEW_THREAD)
                .columns(REVIEW_THREAD.ID, REVIEW_THREAD.REVIEW_ID, REVIEW_THREAD.ENTITY_REF,
                        REVIEW_THREAD.DIFF_ID, REVIEW_THREAD.STATUS, REVIEW_THREAD.CREATED_AT)
                .values(id, reviewId, entityRef, diffId, status, now.toLocalDateTime())
                .execute();
    }

    public boolean updateThreadStatus(String reviewId, String threadId, String status) {
        return dsl.update(REVIEW_THREAD)
                .set(REVIEW_THREAD.STATUS, status)
                .where(REVIEW_THREAD.ID.eq(threadId))
                .and(REVIEW_THREAD.REVIEW_ID.eq(reviewId))
                .execute() == 1;
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
        String ref = r.get(TIMELINE_COMMENT.ENTITY_REF);
        ReviewFrameAnchor anchor = null;
        if (ref != null && ref.startsWith(FRAME_PREFIX)) {
            String[] parts = ref.substring(FRAME_PREFIX.length()).split("\\|", -1);
            if (parts.length != 3) throw new IllegalStateException("Malformed saved review frame anchor");
            anchor = new ReviewFrameAnchor(ReviewFrameAnchor.Side.valueOf(parts[0]), parts[1], parts[2]);
            ref = null;
        }
        return new CommentRow(
                r.get(TIMELINE_COMMENT.ID),
                r.get(TIMELINE_COMMENT.REVIEW_ID),
                r.get(TIMELINE_COMMENT.THREAD_ID),
                r.get(TIMELINE_COMMENT.REVISION_ID),
                ref,
                r.get(TIMELINE_COMMENT.AUTHOR_USER_ID),
                r.get(TIMELINE_COMMENT.CONTENT),
                r.get(TIMELINE_COMMENT.CREATED_AT).atOffset(java.time.ZoneOffset.UTC), anchor);
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

}
