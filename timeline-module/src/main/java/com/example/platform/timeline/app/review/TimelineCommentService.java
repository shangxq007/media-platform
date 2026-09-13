package com.example.platform.timeline.app.review;
import com.example.platform.timeline.api.review.*;
import com.example.platform.timeline.api.event.*;
import com.example.platform.timeline.api.review.ReviewRecords.*;
import com.example.platform.timeline.infrastructure.review.TimelineReviewRepository;

import com.example.platform.timeline.diff.merge.EntityKind;
import com.example.platform.timeline.diff.merge.EntityRef;
import com.example.platform.timeline.diff.merge.TimelineComment;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineCommentService implements TimelineComments {

    private final TimelineReviewRepository reviewRepository;

    private final TimelineReviewService reviews; private final ReviewAuthorization auth; private final ReviewEventPublisher events;
    public TimelineCommentService(TimelineReviewRepository reviewRepository,TimelineReviewService reviews,ReviewAuthorization auth,ReviewEventPublisher events) {
        this.reviewRepository = reviewRepository;this.reviews=reviews;this.auth=auth;this.events=events;
    }

    @Transactional
    public TimelineComment addComment(String reviewId, String revisionId,
                                        String threadId, EntityRef entityRef,
                                        String authorUserId, String content) {
        var review=reviews.requireReview(reviewId,true);
        var actor=auth.require(review.projectId(),true);
        if(!actor.actorId().equals(authorUserId))throw new IllegalArgumentException("comment author mismatch");
        if(!java.util.Objects.equals(revisionId,review.revisionId()))throw new IllegalArgumentException("comment revision differs from review");
        if(content==null||content.isBlank())throw new IllegalArgumentException("comment required");
        if(threadId!=null&&reviewRepository.listThreadsByReview(reviewId).stream().noneMatch(t->t.id().equals(threadId)))throw new IllegalArgumentException("thread not in review");
        String commentId = ("tcom_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        OffsetDateTime now = OffsetDateTime.now();

        String effectiveThreadId = threadId;
        if (effectiveThreadId == null && entityRef != null) {
            effectiveThreadId = ("tthr_" + java.util.UUID.randomUUID().toString().replace("-", ""));
            reviewRepository.insertThread(effectiveThreadId, reviewId,
                    entityRef.key(), null, "OPEN", now);
        }

        reviewRepository.insertComment(commentId, reviewId, effectiveThreadId,
                revisionId, entityRef != null ? entityRef.key() : null,
                authorUserId, content, now);

        if("TIMELINE".equals(reviewRepository.targetType(reviewId)))events.publish(new TimelineReviewCommentAddedEvent(new TimelineReviewReference(reviewId,new TimelineRevisionIdentity(review.tenantId(),review.projectId(),revisionId)),commentId,authorUserId,entityRef!=null?entityRef.key():null,now.toInstant()));
        return TimelineComment.create(commentId, reviewId, effectiveThreadId,
                revisionId, entityRef, authorUserId, content);
    }

    public List<CommentRow> listComments(String reviewId) {
        reviews.requireReview(reviewId,false);return reviewRepository.listCommentsByReview(reviewId);
    }

    @Transactional
    public boolean resolveThread(String reviewId, String threadId) {
        var review=reviews.requireReview(reviewId,true);
        var thread=reviewRepository.listThreadsByReview(reviewId).stream().filter(t->t.id().equals(threadId)).findFirst();
        if(thread.isEmpty())return false;if("RESOLVED".equals(thread.get().status()))return true;
        boolean changed=reviewRepository.updateThreadStatus(reviewId,threadId,"RESOLVED");
        if(changed&&"TIMELINE".equals(reviewRepository.targetType(reviewId)))events.publish(new TimelineReviewThreadResolvedEvent(new TimelineReviewReference(reviewId,new TimelineRevisionIdentity(review.tenantId(),review.projectId(),review.revisionId())),java.util.UUID.randomUUID().toString(),threadId,thread.get().entityRef(),java.time.Instant.now()));
        return changed;
    }

    @Transactional
    public boolean reopenThread(String reviewId, String threadId) {
        reviews.requireReview(reviewId,true);return reviewRepository.updateThreadStatus(reviewId, threadId, "OPEN");
    }

    public List<ThreadRow> listThreads(String reviewId) {
        reviews.requireReview(reviewId,false);return reviewRepository.listThreadsByReview(reviewId);
    }
}
