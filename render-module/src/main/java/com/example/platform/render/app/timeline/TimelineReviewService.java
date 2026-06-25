package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.ReviewDecision;
import com.example.platform.render.domain.timeline.internal.TimelineReview;
import com.example.platform.render.domain.timeline.internal.TimelineReview.ReviewStatus;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineReviewService {

    private final TimelineReviewRepository reviewRepository;

    public TimelineReviewService(TimelineReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public TimelineReview createReview(String projectId, String revisionId,
                                          String authorUserId, String title, String description) {
        String tenantId = TenantContext.get();
        String reviewId = Ids.newId("trev");
        OffsetDateTime now = OffsetDateTime.now();
        reviewRepository.insertReview(reviewId, projectId, tenantId, revisionId,
                authorUserId, title, description, "OPEN", now);
        return TimelineReview.create(reviewId, projectId, tenantId, revisionId,
                authorUserId, title, description);
    }

    public Optional<TimelineReviewRepository.ReviewRow> getReview(String reviewId) {
        return reviewRepository.findById(reviewId);
    }

    public List<TimelineReviewRepository.ReviewRow> listReviews(String projectId, int limit) {
        return reviewRepository.listByProject(projectId, limit);
    }

    @Transactional
    public void approve(String reviewId, String reviewerUserId) {
        updateStatus(reviewId, ReviewStatus.APPROVED.name());
    }

    @Transactional
    public void requestChanges(String reviewId, String reviewerUserId) {
        updateStatus(reviewId, ReviewStatus.CHANGES_REQUESTED.name());
    }

    @Transactional
    public void reject(String reviewId) {
        updateStatus(reviewId, ReviewStatus.CLOSED.name());
    }

    @Transactional
    public void closeReview(String reviewId) {
        updateStatus(reviewId, ReviewStatus.CLOSED.name());
    }

    private void updateStatus(String reviewId, String status) {
        reviewRepository.updateReviewStatus(reviewId, status);
    }

    /**
     * Check whether a review allows merge.
     * Merge is blocked if: review is not APPROVED, or has pending REQUEST_CHANGES.
     */
    public MergeGuardResult checkMergeGuard(String reviewId) {
        var review = reviewRepository.findById(reviewId);
        if (review.isEmpty()) {
            return MergeGuardResult.blocked("Review not found");
        }
        String status = review.get().status();
        if ("APPROVED".equals(status) || "MERGED".equals(status)) {
            return MergeGuardResult.allowed();
        }
        if ("CHANGES_REQUESTED".equals(status)) {
            return MergeGuardResult.blocked("Changes requested — resolve before merging");
        }
        if ("OPEN".equals(status)) {
            List<TimelineReviewRepository.DecisionRow> decisions =
                    reviewRepository.listDecisionsByReview(reviewId);
            boolean hasApproval = decisions.stream()
                    .anyMatch(d -> "APPROVE".equals(d.decision()));
            boolean hasBlocking = decisions.stream()
                    .anyMatch(d -> "REQUEST_CHANGES".equals(d.decision()));
            if (hasBlocking) {
                return MergeGuardResult.blocked("Review has pending change requests");
            }
            if (hasApproval) {
                return MergeGuardResult.allowed();
            }
            return MergeGuardResult.blocked("Review is OPEN — approval required");
        }
        return MergeGuardResult.blocked("Review is " + status + " — cannot merge");
    }

    public record MergeGuardResult(boolean canMerge, String reason) {
        public static MergeGuardResult allowed() {
            return new MergeGuardResult(true, null);
        }
        public static MergeGuardResult blocked(String reason) {
            return new MergeGuardResult(false, reason);
        }
    }
}
