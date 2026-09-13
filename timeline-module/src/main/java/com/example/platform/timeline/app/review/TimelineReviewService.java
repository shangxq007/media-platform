package com.example.platform.timeline.app.review;
import com.example.platform.timeline.api.review.*;
import com.example.platform.timeline.api.review.ReviewRecords.*;
import com.example.platform.timeline.infrastructure.review.TimelineReviewRepository;

import com.example.platform.timeline.diff.merge.ReviewDecision;
import com.example.platform.timeline.diff.merge.TimelineReview;
import com.example.platform.timeline.diff.merge.TimelineReview.ReviewStatus;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineReviewService implements TimelineReviews {

    private final TimelineReviewRepository reviewRepository;

    private final ReviewAuthorization auth;
    private final ReviewEventPublisher events;
    private final com.example.platform.timeline.app.TimelineRevisionQueryService revisions;
    public TimelineReviewService(TimelineReviewRepository reviewRepository,ReviewAuthorization auth,ReviewEventPublisher events,com.example.platform.timeline.app.TimelineRevisionQueryService revisions) {
        this.reviewRepository = reviewRepository;this.auth=auth;this.events=events;this.revisions=revisions;
    }

    @Transactional
    public TimelineReview createReview(String projectId, String revisionId,
                                          String authorUserId, String title, String description) {
        String tenantId = com.example.platform.shared.web.TenantGuard.requireTenantId();
        var actor=auth.require(projectId,true);
        if(!actor.actorId().equals(authorUserId))throw new IllegalArgumentException("author must be authenticated actor");
        if(revisions.findById(projectId,tenantId,revisionId).isEmpty())throw new IllegalArgumentException("revision not found in project");
        String reviewId = ("trev_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        OffsetDateTime now = OffsetDateTime.now();
        reviewRepository.insertReview(reviewId, projectId, tenantId, revisionId,
                authorUserId, title, description, "OPEN", now);
        events.publish(new com.example.platform.shared.events.ReviewCreatedEvent(reviewId,projectId,"TIMELINE",revisionId,authorUserId,title));
        return TimelineReview.create(reviewId, projectId, tenantId, revisionId,
                authorUserId, title, description);
    }

    public Optional<ReviewRow> getReview(
            String projectId, String tenantId, String reviewId) {
        com.example.platform.shared.web.TenantGuard.assertSameTenant(tenantId);auth.require(projectId,false);
        return reviewRepository.findOwnedById(reviewId, projectId, tenantId);
    }

    public List<ReviewRow> listReviews(
            String projectId, String tenantId, int limit) {
        com.example.platform.shared.web.TenantGuard.assertSameTenant(tenantId);auth.require(projectId,false);
        return reviewRepository.listOwnedByProject(projectId, tenantId, Math.max(1,limit));
    }

    @Transactional
    public void approve(String reviewId, String reviewerUserId) {
        decide(reviewId,reviewerUserId,ReviewStatus.APPROVED.name(),"APPROVE");
    }

    @Transactional
    public void requestChanges(String reviewId, String reviewerUserId) {
        decide(reviewId,reviewerUserId,ReviewStatus.CHANGES_REQUESTED.name(),"REQUEST_CHANGES");
    }

    @Transactional
    public void reject(String reviewId) {
        decide(reviewId,null,ReviewStatus.CLOSED.name(),"REJECT");
    }

    ReviewRow requireReview(String id,boolean write) {
        if(write)reviewRepository.lock(id);
        var row=reviewRepository.findById(id).orElseThrow(()->new IllegalArgumentException("review not found in tenant"));
        auth.require(row.projectId(),write);return row;
    }
    private void decide(String id,String requestedActor,String status,String decision) {
        var row=requireReview(id,true);var actor=auth.require(row.projectId(),true);
        if(requestedActor!=null&&!actor.actorId().equals(requestedActor))throw new IllegalArgumentException("reviewer must be authenticated actor");
        if(status.equals(row.status()))return;
        if("CLOSED".equals(row.status())||"MERGED".equals(row.status()))throw new ReviewConflictException("review is closed");
        reviewRepository.updateReviewStatus(id,status);
        reviewRepository.insertDecision("rdec_"+java.util.UUID.randomUUID(),id,actor.actorId(),decision,OffsetDateTime.now());
        if(!"TIMELINE".equals(reviewRepository.targetType(id)))return;
        switch(decision){
         case "APPROVE" -> events.publish(new com.example.platform.shared.events.ReviewApprovedEvent(id,row.projectId(),"TIMELINE",row.revisionId(),actor.actorId()));
         case "REQUEST_CHANGES" -> events.publish(new com.example.platform.shared.events.ReviewChangesRequestedEvent(id,row.projectId(),"TIMELINE",row.revisionId(),actor.actorId()));
         case "REJECT" -> events.publish(new com.example.platform.shared.events.ReviewRejectedEvent(id,row.projectId(),"TIMELINE",row.revisionId()));
         default -> throw new IllegalArgumentException("unsupported decision");
        }
    }
    @Transactional
    public ReviewRow createAssetReview(String project,String asset,String author,String title,String description) {
        var actor=auth.require(project,true);if(!actor.actorId().equals(author))throw new IllegalArgumentException("author mismatch");
        String id="arev_"+java.util.UUID.randomUUID();
        reviewRepository.insertReview(id,project,actor.tenantId(),asset,actor.actorId(),title,description,"OPEN",OffsetDateTime.now());
        reviewRepository.setTargetType(id,"ASSET");return reviewRepository.findById(id).orElseThrow();
    }

    /**
     * Check whether a review allows merge.
     * Merge is blocked if: review is not APPROVED, or has pending REQUEST_CHANGES.
     */
    public MergeGuardResult checkMergeGuard(String reviewId) {
        var review = java.util.Optional.of(requireReview(reviewId,false));
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
            List<DecisionRow> decisions =
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

}
