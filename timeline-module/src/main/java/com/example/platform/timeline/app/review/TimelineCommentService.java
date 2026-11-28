package com.example.platform.timeline.app.review;
import com.example.platform.timeline.api.review.*;
import com.example.platform.timeline.api.event.*;
import com.example.platform.timeline.api.review.ReviewRecords.*;
import com.example.platform.timeline.infrastructure.review.TimelineReviewRepository;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantGuard;

import com.example.platform.timeline.diff.merge.EntityRef;
import com.example.platform.timeline.diff.merge.TimelineComment;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineCommentService implements TimelineComments {

    private final TimelineReviewRepository reviewRepository;

    private final TimelineReviewService reviews; private final ReviewAuthorization auth; private final ReviewEventPublisher events;
    private final TimelineRevisionQueryService revisions;

    public TimelineCommentService(TimelineReviewRepository reviewRepository, TimelineReviewService reviews,
                                  ReviewAuthorization auth, ReviewEventPublisher events, TimelineRevisionQueryService revisions) {
        this.reviewRepository = reviewRepository;this.reviews=reviews;this.auth=auth;this.events=events;this.revisions=revisions;
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

    @Override
    @Transactional
    public CommentRow addFrameComment(String projectId, String reviewId, String revisionId, String authorUserId,
                                      String content, ReviewFrameAnchor anchor, UUID requestId) {
        var actor = auth.require(projectId, true);
        if (!actor.actorId().equals(authorUserId)) throw new IllegalArgumentException("comment author mismatch");
        if (anchor == null || requestId == null || content == null || content.isBlank() || content.length() > 4000) {
            throw new IllegalArgumentException("Frame comments require a request ID, an anchor and 1 to 4000 characters");
        }
        if (revisionId == null || revisionId.isBlank()) throw new IllegalArgumentException("revision required");
        String tenantId = actor.tenantId();
        ReviewRow review = reviewId == null ? null : reviewRepository.findOwnedById(reviewId, projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("review not found in project"));
        if (review != null && !revisionId.equals(review.revisionId())) {
            throw new IllegalArgumentException("comment revision differs from review");
        }
        requireFrameWithinRevision(projectId, tenantId, revisionId, anchor);
        String id = scopedId("tfc_", tenantId, projectId, authorUserId, requestId.toString());
        if (review == null) review = reviews.frameReview(projectId, tenantId, revisionId, authorUserId);

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean created = reviewRepository.insertFrameComment(id, review.id(), revisionId, anchor, authorUserId, content, now);
        var row = reviewRepository.findOwnedComment(projectId, tenantId, id)
                .orElseThrow(() -> new ReviewConflictException("Comment request ID conflicts with another target"));
        if (!review.id().equals(row.reviewId()) || !revisionId.equals(row.revisionId())
                || !authorUserId.equals(row.authorUserId()) || !anchor.equals(row.frameAnchor())
                || !content.equals(row.content())) {
            throw new ReviewConflictException("Comment request ID was already used for a different comment");
        }
        if (created) {
            events.publish(new TimelineReviewCommentAddedEvent(new TimelineReviewReference(review.id(),
                    new TimelineRevisionIdentity(tenantId, projectId, revisionId)), id, authorUserId, null,
                    row.createdAt().toInstant()));
        }
        return row;
    }

    @Override
    public List<CommentRow> listProjectComments(String projectId) {
        auth.require(projectId, false);
        return reviewRepository.listOwnedProjectComments(projectId, TenantGuard.requireTenantId());
    }

    private void requireFrameWithinRevision(String projectId, String tenantId, String revisionId, ReviewFrameAnchor anchor) {
        var snapshot = revisions.getRevisionSnapshotPayload(projectId, tenantId, revisionId)
                .orElseThrow(() -> new IllegalArgumentException("revision snapshot not found in project"));
        var document = TimelineDocumentJsonSerializer.deserialize(snapshot.canonicalTimelineJson());
        MediaTime end = MediaTime.ZERO;
        for (var track : document.getTracks()) {
            for (var clip : track.clips()) end = end.max(clip.getEndTime());
        }
        for (var text : document.getTextElements()) {
            if (text.subtitleCue() == null) continue;
            var start = MediaTime.ofTicks(text.start().numerator().longValueExact(), text.start().denominator().longValueExact());
            var duration = MediaTime.ofTicks(text.duration().numerator().longValueExact(), text.duration().denominator().longValueExact());
            end = end.max(start.add(duration));
        }
        if (anchor.time().compareTo(end) >= 0) {
            throw new IllegalArgumentException("Frame position is outside the saved revision's half-open timeline extent");
        }
    }

    static String scopedId(String prefix, String... parts) {
        var key = new StringBuilder(prefix);
        for (String part : parts) key.append(part.length()).append(':').append(part);
        return prefix + UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
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
