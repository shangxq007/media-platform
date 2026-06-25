package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.EntityKind;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.TimelineComment;
import com.example.platform.shared.Ids;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineCommentService {

    private final TimelineReviewRepository reviewRepository;

    public TimelineCommentService(TimelineReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public TimelineComment addComment(String reviewId, String revisionId,
                                        String threadId, EntityRef entityRef,
                                        String authorUserId, String content) {
        String commentId = Ids.newId("tcom");
        OffsetDateTime now = OffsetDateTime.now();

        String effectiveThreadId = threadId;
        if (effectiveThreadId == null && entityRef != null) {
            effectiveThreadId = Ids.newId("tthr");
            reviewRepository.insertThread(effectiveThreadId, reviewId,
                    entityRef.key(), null, "OPEN", now);
        }

        reviewRepository.insertComment(commentId, reviewId, effectiveThreadId,
                revisionId, entityRef != null ? entityRef.key() : null,
                authorUserId, content, now);

        return TimelineComment.create(commentId, reviewId, effectiveThreadId,
                revisionId, entityRef, authorUserId, content);
    }

    public List<TimelineReviewRepository.CommentRow> listComments(String reviewId) {
        return reviewRepository.listCommentsByReview(reviewId);
    }

    @Transactional
    public void resolveThread(String threadId) {
        reviewRepository.updateThreadStatus(threadId, "RESOLVED");
    }

    @Transactional
    public void reopenThread(String threadId) {
        reviewRepository.updateThreadStatus(threadId, "OPEN");
    }

    public List<TimelineReviewRepository.ThreadRow> listThreads(String reviewId) {
        return reviewRepository.listThreadsByReview(reviewId);
    }
}
