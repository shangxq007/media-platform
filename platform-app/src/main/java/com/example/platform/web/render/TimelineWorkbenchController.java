package com.example.platform.web.render;

import com.example.platform.render.app.timeline.*;
import com.example.platform.render.app.timeline.TimelineRevisionService.RevisionFacets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/timelines/{projectId}")
@Tag(name = "Timeline Workbench", description = "Aggregated workbench APIs for frontend consumption")
public class TimelineWorkbenchController {

    private static final Logger log = LoggerFactory.getLogger(TimelineWorkbenchController.class);
    private final TimelineRevisionService revisionService;
    private final TimelineReviewService reviewService;
    private final TimelineReviewRepository reviewRepo;
    private final TimelineCommentService commentService;

    public TimelineWorkbenchController(TimelineRevisionService revisionService,
                                        TimelineReviewService reviewService,
                                        TimelineReviewRepository reviewRepo,
                                        TimelineCommentService commentService) {
        this.revisionService = revisionService;
        this.reviewService = reviewService;
        this.reviewRepo = reviewRepo;
        this.commentService = commentService;
    }

    @GetMapping("/{timelineId}/workbench")
    @Operation(summary = "Get aggregated timeline workbench view")
    public ResponseEntity<WorkbenchDto> workbench(
            @PathVariable String projectId, @PathVariable String timelineId) {
        long start = System.currentTimeMillis();
        RevisionFacets facets = revisionService.listFacets(projectId);
        var reviews = reviewRepo.listByProject(projectId, 20);
        int openComments = reviews.stream()
                .mapToInt(r -> commentService.listComments(r.id()).size()).sum();

        var dto = new WorkbenchDto(timelineId, facets.sources().size(),
                reviews.size(), openComments);
        log.info("Timeline workbench loaded: timeline={} latency={}ms",
                timelineId, System.currentTimeMillis() - start);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{timelineId}/reviews/workspace")
    @Operation(summary = "Get review workspace summary")
    public ReviewWsDto reviewWorkspace(
            @PathVariable String projectId, @PathVariable String timelineId) {
        var reviews = reviewRepo.listByProject(projectId, 20);
        int open = 0, approved = 0, changes = 0, merged = 0;
        for (var r : reviews) {
            switch (r.status()) {
                case "OPEN" -> open++;
                case "APPROVED" -> approved++;
                case "CHANGES_REQUESTED" -> changes++;
                case "MERGED" -> merged++;
            }
        }
        return new ReviewWsDto(open, approved, changes, merged, reviews.size());
    }

    @GetMapping("/{timelineId}/reviews/{reviewId}/workspace")
    @Operation(summary = "Get single review workspace detail")
    public ResponseEntity<ReviewDetailDto> reviewDetail(
            @PathVariable String projectId, @PathVariable String timelineId,
            @PathVariable String reviewId) {
        return reviewRepo.findById(reviewId).map(r -> {
            var cmts = commentService.listComments(reviewId);
            var threads = commentService.listThreads(reviewId);
            long openT = threads.stream().filter(t -> "OPEN".equals(t.status())).count();
            var guard = reviewService.checkMergeGuard(reviewId);
            return ResponseEntity.ok(new ReviewDetailDto(r.id(), r.status(), r.authorUserId(),
                    r.title(), threads.size(), (int) openT,
                    (int) (threads.size() - openT), cmts.size(), guard.canMerge(),
                    guard.reason()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{timelineId}/diff-preview")
    @Operation(summary = "Get diff preview between two revisions")
    public DiffPreviewDto diffPreview(
            @PathVariable String projectId, @PathVariable String timelineId,
            @RequestParam String from, @RequestParam String to) {
        var diff = revisionService.compareRevisions(projectId, from, to);
        int changes = diff.entityChanges() != null ? diff.entityChanges().size() : 0;
        return new DiffPreviewDto(from, to, diff.summary() != null, changes);
    }

    @GetMapping("/{timelineId}/conflicts")
    @Operation(summary = "Get merge conflict preview")
    public ConflictDto conflicts(
            @PathVariable String projectId, @PathVariable String timelineId,
            @RequestParam String base, @RequestParam String source, @RequestParam String target) {
        var sDiff = revisionService.compareRevisions(projectId, base, source);
        var tDiff = revisionService.compareRevisions(projectId, base, target);
        int sc = sDiff.entityChanges() != null ? sDiff.entityChanges().size() : 0;
        int tc = tDiff.entityChanges() != null ? tDiff.entityChanges().size() : 0;
        return new ConflictDto(sc, tc, "Conflicts determined at merge time");
    }

    public record WorkbenchDto(String timelineId, int sourceCount, int reviewCount, int openComments) {}
    public record ReviewWsDto(int open, int approved, int changesRequested, int merged, int total) {}
    public record ReviewDetailDto(String reviewId, String status, String author, String title,
            int threads, int openThreads, int resolvedThreads, int comments,
            boolean canMerge, String mergeReason) {}
    public record DiffPreviewDto(String fromRevision, String toRevision,
            boolean hasDetail, int totalChanges) {}
    public record ConflictDto(int sourceChanges, int targetChanges, String reason) {}
}
