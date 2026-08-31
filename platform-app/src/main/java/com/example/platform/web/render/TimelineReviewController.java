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
import com.example.platform.timeline.diff.merge.EntityKind;
import com.example.platform.timeline.diff.merge.EntityRef;
import com.example.platform.timeline.diff.merge.ReviewDecision;
import com.example.platform.timeline.diff.merge.TimelineComment;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/render/projects/{projectId}/timeline/reviews")
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
        throw FailClosedAuthorization.unavailable("timeline review creation");
    }

    @GetMapping
    @Operation(summary = "List reviews for a project")
    public List<ReviewResponse> listReviews(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "30") int limit) {
        throw FailClosedAuthorization.unavailable("timeline review listing");
    }

    @GetMapping("/{reviewId}")
    @Operation(summary = "Get review with comments and threads")
    public ResponseEntity<ReviewDetailResponse> getReview(
            @PathVariable String projectId,
            @PathVariable String reviewId) {
        throw FailClosedAuthorization.unavailable("timeline review read");
    }

    @PostMapping("/{reviewId}/comments")
    @Operation(summary = "Add a comment to a review")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @RequestBody AddCommentRequest body) {
        throw FailClosedAuthorization.unavailable("timeline review comment creation");
    }

    @GetMapping("/{reviewId}/comments")
    @Operation(summary = "List comments on a review")
    public List<CommentResponse> listComments(
            @PathVariable String projectId,
            @PathVariable String reviewId) {
        throw FailClosedAuthorization.unavailable("timeline review comment listing");
    }

    @PostMapping("/{reviewId}/comments/{threadId}/resolve")
    @Operation(summary = "Resolve a comment thread")
    public ResponseEntity<Map<String, Object>> resolveThread(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @PathVariable String threadId) {
        throw FailClosedAuthorization.unavailable("timeline review thread resolution");
    }

    @PostMapping("/{reviewId}/comments/{threadId}/reopen")
    @Operation(summary = "Reopen a resolved comment thread")
    public ResponseEntity<Map<String, Object>> reopenThread(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @PathVariable String threadId) {
        throw FailClosedAuthorization.unavailable("timeline review thread reopen");
    }

    @PostMapping("/{reviewId}/approve")
    @Operation(summary = "Approve a review")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @RequestParam String reviewerUserId) {
        throw FailClosedAuthorization.unavailable("timeline review approval");
    }

    @PostMapping("/{reviewId}/request-changes")
    @Operation(summary = "Request changes on a review")
    public ResponseEntity<Map<String, Object>> requestChanges(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @RequestParam String reviewerUserId) {
        throw FailClosedAuthorization.unavailable("timeline review change request");
    }

    @PostMapping("/{reviewId}/reject")
    @Operation(summary = "Reject a review")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable String projectId,
            @PathVariable String reviewId,
            @RequestParam String reviewerUserId) {
        throw FailClosedAuthorization.unavailable("timeline review rejection");
    }

    @GetMapping("/{reviewId}/merge-guard")
    @Operation(summary = "Check if a review allows merge")
    public ResponseEntity<Map<String, Object>> checkMergeGuard(
            @PathVariable String projectId,
            @PathVariable String reviewId) {
        throw FailClosedAuthorization.unavailable("timeline review merge guard");
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
