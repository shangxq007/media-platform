package com.example.platform.web.render;

import com.example.platform.render.app.timeline.TimelineCommentService;
import com.example.platform.render.app.timeline.TimelineReviewRepository;
import com.example.platform.render.app.timeline.TimelineReviewService;
import com.example.platform.render.app.timeline.ReviewDecisionService;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.shared.events.ReviewCreatedEvent;
import com.example.platform.shared.events.ReviewApprovedEvent;
import com.example.platform.shared.events.ReviewRejectedEvent;
import com.example.platform.shared.events.ReviewChangesRequestedEvent;
import com.example.platform.shared.events.ReviewCommentAddedEvent;
import com.example.platform.shared.events.ReviewThreadResolvedEvent;
import com.example.platform.render.app.timeline.TimelineReviewService.MergeGuardResult;
import com.example.platform.render.domain.timeline.internal.EntityKind;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.ReviewDecision;
import com.example.platform.render.domain.timeline.internal.TimelineComment;
import com.example.platform.shared.web.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/render/projects/{projectId}/timeline/reviews")
@Tag(name = "Timeline Reviews", description = "Review workflow for timeline revisions")
public class TimelineReviewController {

    private final TimelineReviewService reviewService;
    private final TimelineCommentService commentService;
    private final ReviewDecisionService decisionService;
    private final TimelineReviewEventPublisher eventPublisher;

    public TimelineReviewController(TimelineReviewService reviewService,
                                     TimelineCommentService commentService,
                                     ReviewDecisionService decisionService,
                                     TimelineReviewEventPublisher eventPublisher) {
        this.reviewService = reviewService;
        this.commentService = commentService;
        this.decisionService = decisionService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @Operation(summary = "Create a review for a timeline revision")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable String projectId,
            @RequestBody CreateReviewRequest body) {
        var review = reviewService.createReview(projectId,
                body.revisionId(), body.authorUserId(), body.title(), body.description());
        eventPublisher.publish(new ReviewCreatedEvent(review.reviewId(), projectId,
                "TIMELINE", body.revisionId(), body.authorUserId(), body.title()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(reviewService.getReview(review.reviewId()).orElseThrow()));
    }

    @GetMapping
    @Operation(summary = "List reviews for a project")
    public List<ReviewResponse> listReviews(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "30") int limit) {
        return reviewService.listReviews(projectId, limit).stream()
                .map(TimelineReviewController::toResponse)
                .toList();
    }

    @GetMapping("/{reviewId}")
    @Operation(summary = "Get review with comments and threads")
    public ResponseEntity<ReviewDetailResponse> getReview(
            @PathVariable String projectId,
            @PathVariable String reviewId) {
        return reviewService.getReview(reviewId)
                .map(r -> {
                    var comments = commentService.listComments(reviewId);
                    var threads = commentService.listThreads(reviewId);
                    var decisions = decisionService.listDecisions(reviewId);
                    var guard = reviewService.checkMergeGuard(reviewId);
                    return ResponseEntity.ok(toDetailResponse(r, comments, threads, decisions, guard));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{reviewId}/comments")
    @Operation(summary = "Add a comment to a review")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @RequestBody AddCommentRequest body) {
        EntityRef ref = body.entityKind() != null && body.entityId() != null
                ? new EntityRef(EntityKind.valueOf(body.entityKind()), body.entityId())
                : null;
        TimelineComment comment = commentService.addComment(
                reviewId, body.revisionId(), body.threadId(), ref,
                body.authorUserId(), body.content());
        eventPublisher.publish(new ReviewCommentAddedEvent(reviewId, "unknown", "TIMELINE",
                body.revisionId(), comment.commentId(), body.authorUserId(),
                ref != null ? ref.key() : null));
        return ResponseEntity.status(HttpStatus.CREATED).body(toCommentResponse(comment));
    }

    @GetMapping("/{reviewId}/comments")
    @Operation(summary = "List comments on a review")
    public List<CommentResponse> listComments(
            @PathVariable String projectId,
            @PathVariable String reviewId) {
        return commentService.listComments(reviewId).stream()
                .map(TimelineReviewController::toCommentResponse)
                .toList();
    }

    @PostMapping("/{reviewId}/comments/{threadId}/resolve")
    @Operation(summary = "Resolve a comment thread")
    public ResponseEntity<Map<String, Object>> resolveThread(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @PathVariable String threadId) {
        commentService.resolveThread(threadId);
        eventPublisher.publish(new ReviewThreadResolvedEvent(reviewId, "unknown", threadId, "unknown"));
        return ResponseEntity.ok(Map.of("threadId", threadId, "resolved", true));
    }

    @PostMapping("/{reviewId}/comments/{threadId}/reopen")
    @Operation(summary = "Reopen a resolved comment thread")
    public ResponseEntity<Map<String, Object>> reopenThread(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @PathVariable String threadId) {
        commentService.reopenThread(threadId);
        return ResponseEntity.ok(Map.of("threadId", threadId, "reopened", true));
    }

    @PostMapping("/{reviewId}/approve")
    @Operation(summary = "Approve a review")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @RequestParam String reviewerUserId) {
        reviewService.approve(reviewId, reviewerUserId);
        decisionService.recordDecision(reviewId, reviewerUserId, ReviewDecision.Decision.APPROVE);
        eventPublisher.publish(new ReviewApprovedEvent(reviewId, "unknown", "TIMELINE", "unknown", reviewerUserId));
        return ResponseEntity.ok(Map.of("reviewId", reviewId, "status", "APPROVED"));
    }

    @PostMapping("/{reviewId}/request-changes")
    @Operation(summary = "Request changes on a review")
    public ResponseEntity<Map<String, Object>> requestChanges(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @RequestParam String reviewerUserId) {
        reviewService.requestChanges(reviewId, reviewerUserId);
        decisionService.recordDecision(reviewId, reviewerUserId, ReviewDecision.Decision.REQUEST_CHANGES);
        eventPublisher.publish(new ReviewChangesRequestedEvent(reviewId, "unknown", "TIMELINE", "unknown", reviewerUserId));
        return ResponseEntity.ok(Map.of("reviewId", reviewId, "status", "CHANGES_REQUESTED"));
    }

    @PostMapping("/{reviewId}/reject")
    @Operation(summary = "Reject a review")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @RequestParam String reviewerUserId) {
        reviewService.reject(reviewId);
        decisionService.recordDecision(reviewId, reviewerUserId, ReviewDecision.Decision.REJECT);
        eventPublisher.publish(new ReviewRejectedEvent(reviewId, "unknown", "TIMELINE", "unknown"));
        return ResponseEntity.ok(Map.of("reviewId", reviewId, "status", "CLOSED"));
    }

    @GetMapping("/{reviewId}/merge-guard")
    @Operation(summary = "Check if a review allows merge")
    public ResponseEntity<Map<String, Object>> checkMergeGuard(
            @PathVariable String projectId,
            @PathVariable String reviewId) {
        MergeGuardResult guard = reviewService.checkMergeGuard(reviewId);
        return ResponseEntity.ok(Map.of("reviewId", reviewId,
                "canMerge", guard.canMerge(), "reason", guard.reason() != null ? guard.reason() : ""));
    }

    private static ReviewResponse toResponse(TimelineReviewRepository.ReviewRow r) {
        return new ReviewResponse(r.id(), r.projectId(), r.revisionId(),
                r.authorUserId(), r.title(), r.description(), r.status(),
                r.createdAt() != null ? r.createdAt().toString() : null,
                r.updatedAt() != null ? r.updatedAt().toString() : null);
    }

    private static ReviewDetailResponse toDetailResponse(TimelineReviewRepository.ReviewRow r,
                                                           List<TimelineReviewRepository.CommentRow> comments,
                                                           List<TimelineReviewRepository.ThreadRow> threads,
                                                           List<TimelineReviewRepository.DecisionRow> decisions,
                                                           MergeGuardResult guard) {
        return new ReviewDetailResponse(
                toResponse(r),
                comments.stream().map(TimelineReviewController::toCommentResponse).toList(),
                threads.stream().map(TimelineReviewController::toThreadResponse).toList(),
                decisions.stream().map(TimelineReviewController::toDecisionResponse).toList(),
                new MergeGuardDto(guard.canMerge(), guard.reason()));
    }

    private static CommentResponse toCommentResponse(TimelineReviewRepository.CommentRow c) {
        return new CommentResponse(c.id(), c.reviewId(), c.threadId(), c.revisionId(),
                c.entityRef(), c.authorUserId(), c.content(),
                c.createdAt() != null ? c.createdAt().toString() : null);
    }

    private static CommentResponse toCommentResponse(TimelineComment c) {
        return new CommentResponse(c.commentId(), c.reviewId(), c.threadId(), c.revisionId(),
                c.entityRef() != null ? c.entityRef().key() : null,
                c.authorUserId(), c.content(), c.createdAt().toString());
    }

    private static ThreadResponse toThreadResponse(TimelineReviewRepository.ThreadRow t) {
        return new ThreadResponse(t.id(), t.reviewId(), t.entityRef(), t.diffId(), t.status(),
                t.createdAt() != null ? t.createdAt().toString() : null);
    }

    private static DecisionResponse toDecisionResponse(TimelineReviewRepository.DecisionRow d) {
        return new DecisionResponse(d.id(), d.reviewId(), d.reviewerUserId(), d.decision(),
                d.createdAt() != null ? d.createdAt().toString() : null);
    }

    public record CreateReviewRequest(String revisionId, String authorUserId,
                                        String title, String description) {}

    public record AddCommentRequest(String threadId, String revisionId,
                                      String entityKind, String entityId,
                                      String authorUserId, String content) {}

    public record ReviewResponse(String reviewId, String projectId, String revisionId,
                                   String authorUserId, String title, String description,
                                   String status, String createdAt, String updatedAt) {}

    public record ReviewDetailResponse(ReviewResponse review, List<CommentResponse> comments,
                                         List<ThreadResponse> threads,
                                         List<DecisionResponse> decisions,
                                         MergeGuardDto mergeGuard) {}

    public record CommentResponse(String commentId, String reviewId, String threadId,
                                    String revisionId, String entityRef,
                                    String authorUserId, String content, String createdAt) {}

    public record ThreadResponse(String threadId, String reviewId, String entityRef,
                                   String diffId, String status, String createdAt) {}

    public record DecisionResponse(String decisionId, String reviewId, String reviewerUserId,
                                     String decision, String createdAt) {}

    public record MergeGuardDto(boolean canMerge, String reason) {}
}
