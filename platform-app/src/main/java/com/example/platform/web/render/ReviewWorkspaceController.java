package com.example.platform.web.render;

import com.example.platform.render.app.timeline.*;
import com.example.platform.render.domain.timeline.internal.ReviewDecision;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "Review Workspace", description = "Aggregated review workspace APIs for frontend")
public class ReviewWorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(ReviewWorkspaceController.class);
    private final TimelineReviewService reviewService;
    private final TimelineReviewRepository reviewRepo;
    private final TimelineCommentService commentService;
    private final ReviewDecisionService decisionService;

    public ReviewWorkspaceController(TimelineReviewService reviewService,
                                       TimelineReviewRepository reviewRepo,
                                       TimelineCommentService commentService,
                                       ReviewDecisionService decisionService) {
        this.reviewService = reviewService;
        this.reviewRepo = reviewRepo;
        this.commentService = commentService;
        this.decisionService = decisionService;
    }

    @GetMapping("/{reviewId}/workspace")
    @Operation(summary = "Full review workspace summary")
    public ResponseEntity<ReviewWorkspaceDto> workspace(@PathVariable String reviewId) {
        long start = System.currentTimeMillis();
        return reviewRepo.findById(reviewId).map(r -> {
            var cmts = commentService.listComments(reviewId);
            var threads = commentService.listThreads(reviewId);
            var decisions = decisionService.listDecisions(reviewId);
            long openThreads = threads.stream().filter(t -> "OPEN".equals(t.status())).count();
            int approveCount = (int) decisions.stream().filter(d -> "APPROVE".equals(d.decision())).count();
            int rejectCount = (int) decisions.stream().filter(d -> "REJECT".equals(d.decision())).count();
            int changeCount = (int) decisions.stream().filter(d -> "REQUEST_CHANGES".equals(d.decision())).count();
            var guard = reviewService.checkMergeGuard(reviewId);

            var dto = new ReviewWorkspaceDto(r.id(), r.revisionId(), r.title(), r.status(),
                    r.authorUserId(), r.createdAt() != null ? r.createdAt().toString() : null,
                    r.updatedAt() != null ? r.updatedAt().toString() : null,
                    approveCount, rejectCount, changeCount,
                    (int) openThreads, threads.size() - (int) openThreads, cmts.size(),
                    guard.canMerge(), guard.reason());
            log.info("Review workspace loaded: review={} threads={} comments={} latency={}ms",
                    reviewId, threads.size(), cmts.size(), System.currentTimeMillis() - start);
            return ResponseEntity.ok(dto);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{reviewId}/threads")
    @Operation(summary = "List review threads for sidebar")
    public List<ReviewThreadDto> threads(@PathVariable String reviewId) {
        return commentService.listThreads(reviewId).stream()
                .map(t -> {
                    var threadComments = commentService.listComments(reviewId).stream()
                            .filter(c -> t.id().equals(c.threadId())).toList();
                    return new ReviewThreadDto(t.id(), t.entityRef(), t.status(),
                            threadComments.size(), threadComments.isEmpty() ? null :
                                    threadComments.get(threadComments.size() - 1).createdAt() != null ?
                                            threadComments.get(threadComments.size() - 1).createdAt().toString() : null);
                }).toList();
    }

    @GetMapping("/{reviewId}/comments")
    @Operation(summary = "List review comments")
    public List<ReviewCommentDto> comments(@PathVariable String reviewId,
            @RequestParam(required = false) String threadId) {
        return commentService.listComments(reviewId).stream()
                .filter(c -> threadId == null || threadId.equals(c.threadId()))
                .map(c -> new ReviewCommentDto(c.id(), c.threadId(), c.entityRef(),
                        c.authorUserId(), c.content(),
                        c.createdAt() != null ? c.createdAt().toString() : null))
                .toList();
    }

    @GetMapping("/{reviewId}/anchors")
    @Operation(summary = "Entity anchor summary for timeline overlay")
    public List<EntityAnchorDto> anchors(@PathVariable String reviewId) {
        var cmts = commentService.listComments(reviewId);
        var threads = commentService.listThreads(reviewId);
        Map<String, EntityAnchorDto> anchorMap = new LinkedHashMap<>();

        for (var t : threads) {
            if (t.entityRef() != null) {
                anchorMap.computeIfAbsent(t.entityRef(),
                        k -> new EntityAnchorDto(k, 0, 0, 0));
                var a = anchorMap.get(t.entityRef());
                anchorMap.put(t.entityRef(), new EntityAnchorDto(a.entityRef(),
                        a.threadCount() + 1, a.commentCount(), a.openIssues() + ("OPEN".equals(t.status()) ? 1 : 0)));
            }
        }
        for (var c : cmts) {
            if (c.entityRef() != null) {
                anchorMap.computeIfAbsent(c.entityRef(),
                        k -> new EntityAnchorDto(k, 0, 0, 0));
                var a = anchorMap.get(c.entityRef());
                anchorMap.put(c.entityRef(), new EntityAnchorDto(a.entityRef(),
                        a.threadCount(), a.commentCount() + 1, a.openIssues()));
            }
        }
        return new ArrayList<>(anchorMap.values());
    }

    @GetMapping("/{reviewId}/decisions")
    @Operation(summary = "Decision summary overview")
    public ResponseEntity<DecisionSummaryDto> decisions(@PathVariable String reviewId) {
        var decisions = decisionService.listDecisions(reviewId);
        int approve = (int) decisions.stream().filter(d -> "APPROVE".equals(d.decision())).count();
        int reject = (int) decisions.stream().filter(d -> "REJECT".equals(d.decision())).count();
        int changes = (int) decisions.stream().filter(d -> "REQUEST_CHANGES".equals(d.decision())).count();
        var last = decisions.isEmpty() ? null : decisions.get(decisions.size() - 1);
        return ResponseEntity.ok(new DecisionSummaryDto(approve, reject, changes,
                last != null ? last.decision() : null,
                last != null ? last.reviewerUserId() : null,
                last != null && last.createdAt() != null ? last.createdAt().toString() : null));
    }

    @GetMapping("/{reviewId}/merge-guard")
    @Operation(summary = "Merge guard for merge button")
    public ResponseEntity<MergeGuardDto> mergeGuard(@PathVariable String reviewId) {
        var guard = reviewService.checkMergeGuard(reviewId);
        var threads = commentService.listThreads(reviewId);
        long pendingThreads = threads.stream().filter(t -> "OPEN".equals(t.status())).count();
        var decisions = decisionService.listDecisions(reviewId);
        int approvals = (int) decisions.stream().filter(d -> "APPROVE".equals(d.decision())).count();
        int pendingChanges = (int) decisions.stream().filter(d -> "REQUEST_CHANGES".equals(d.decision())).count();

        return ResponseEntity.ok(new MergeGuardDto(guard.canMerge(), guard.reason(),
                (int) pendingThreads, pendingChanges, 1, approvals));
    }

    public record ReviewWorkspaceDto(String reviewId, String revisionId, String title, String status,
            String authorId, String createdAt, String updatedAt,
            int approvals, int rejections, int changeRequests,
            int openThreads, int resolvedThreads, int commentCount,
            boolean mergeAllowed, String mergeReason) {}

    public record ReviewThreadDto(String threadId, String entityRef, String status,
            int commentCount, String lastActivity) {}

    public record ReviewCommentDto(String commentId, String threadId, String entityRef,
            String authorId, String content, String createdAt) {}

    public record EntityAnchorDto(String entityRef, int threadCount, int commentCount, int openIssues) {}

    public record DecisionSummaryDto(int approveCount, int rejectCount, int changeRequestCount,
            String lastDecision, String lastDecisionBy, String lastDecisionAt) {}

    public record MergeGuardDto(boolean allowed, String reason, int pendingThreads,
            int pendingChangeRequests, int requiredApprovals, int currentApprovals) {}
}
