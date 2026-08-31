package com.example.platform.web.assets;

import com.example.platform.render.app.asset.AssetReviewService;
import com.example.platform.render.app.timeline.TimelineReviewRepository;
import com.example.platform.render.domain.asset.AssetPublishStatus;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.shared.events.*;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/assets/{assetId}")
@Tag(name = "Asset Review & Publish", description = "Review and publish workflow for assets")
public class AssetPublishController {

    private final AssetReviewService reviewService;
    private final TimelineReviewEventPublisher eventPublisher;

    public AssetPublishController(AssetReviewService reviewService,
                                    TimelineReviewEventPublisher eventPublisher) {
        this.reviewService = reviewService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/submit-review")
    @Operation(summary = "Submit asset for review")
    public ResponseEntity<ReviewResponseDto> submitReview(
            @PathVariable String projectId,
            @PathVariable String assetId,
            @RequestBody SubmitReviewRequest body) {
        throw FailClosedAuthorization.unavailable("asset review submission");
    }

    @GetMapping("/review")
    @Operation(summary = "Get asset review status")
    public ResponseEntity<ReviewResponseDto> getReview(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        return reviewService.getReview(assetId)
                .map(r -> ResponseEntity.ok(toDto(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/approve-review")
    @Operation(summary = "Approve asset (via review system)")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable String projectId,
            @PathVariable String assetId,
            @RequestParam String reviewerUserId) {
        throw FailClosedAuthorization.unavailable("asset review approval");
    }

    @PostMapping("/reject-review")
    @Operation(summary = "Reject asset review")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        throw FailClosedAuthorization.unavailable("asset review rejection");
    }

    @PostMapping("/publish")
    @Operation(summary = "Publish asset (must be APPROVED)")
    public ResponseEntity<Map<String, Object>> publish(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        throw FailClosedAuthorization.unavailable("asset publication");
    }

    @PostMapping("/archive")
    @Operation(summary = "Archive asset")
    public ResponseEntity<Map<String, Object>> archive(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        throw FailClosedAuthorization.unavailable("asset archival");
    }

    @GetMapping("/publish-status")
    @Operation(summary = "Get asset publish status")
    public ResponseEntity<Map<String, Object>> getPublishStatus(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        return reviewService.getPublishStatus(assetId)
                .map(s -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("assetId", assetId);
                    result.put("publishStatus", s.name());
                    result.put("canPublish", reviewService.canPublish(assetId));
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/review-summary")
    @Operation(summary = "Get review summary for asset")
    public ResponseEntity<Map<String, Object>> reviewSummary(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        var review = reviewService.getReview(assetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", assetId);
        result.put("hasReview", review.isPresent());
        review.ifPresent(r -> {
            result.put("reviewId", r.id());
            result.put("status", r.status());
        });
        return ResponseEntity.ok(result);
    }

    private static ReviewResponseDto toDto(TimelineReviewRepository.ReviewRow r) {
        return new ReviewResponseDto(r.id(), r.revisionId(), r.status(), r.title(),
                r.createdAt() != null ? r.createdAt().toString() : null);
    }

    public record SubmitReviewRequest(String authorUserId, String title, String description) {}
    public record ReviewResponseDto(String reviewId, String targetId, String status,
                                      String title, String createdAt) {}
}
