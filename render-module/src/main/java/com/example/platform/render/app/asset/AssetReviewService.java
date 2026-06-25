package com.example.platform.render.app.asset;

import com.example.platform.render.app.timeline.TimelineReviewRepository;
import com.example.platform.render.app.timeline.TimelineReviewService;
import com.example.platform.render.domain.asset.AssetPublishStatus;
import com.example.platform.render.domain.timeline.internal.ReviewTargetType;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asset review and publish service — reuses the existing TimelineReview system
 * by setting {@code targetType = ASSET} and {@code revisionId = assetId}.
 */
@Service
public class AssetReviewService {

    private final AssetRepository assetRepository;
    private final TimelineReviewService reviewService;
    private final TimelineReviewRepository reviewRepository;

    public AssetReviewService(AssetRepository assetRepository,
                                TimelineReviewService reviewService,
                                TimelineReviewRepository reviewRepository) {
        this.assetRepository = assetRepository;
        this.reviewService = reviewService;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public TimelineReviewRepository.ReviewRow submitForReview(String assetId, String authorUserId,
                                                                 String title, String description) {
        var asset = assetRepository.findById(TenantContext.get(), assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        String reviewId = Ids.newId("arev");
        String tenantId = TenantContext.get();
        OffsetDateTime now = OffsetDateTime.now();

        reviewRepository.insertReview(reviewId, asset.projectId(), tenantId, assetId,
                authorUserId, title, description, "OPEN", now);
        reviewRepository.setTargetType(reviewId, "ASSET");

        return reviewRepository.findById(reviewId).orElseThrow();
    }

    public Optional<TimelineReviewRepository.ReviewRow> getReview(String assetId) {
        return reviewRepository.findByTargetId(assetId);
    }

    public List<TimelineReviewRepository.ReviewRow> listAssetReviews(String projectId, int limit) {
        return reviewRepository.listByProject(projectId, limit);
    }

    @Transactional
    public void approveAsset(String assetId, String reviewerUserId) {
        var review = getReview(assetId)
                .orElseThrow(() -> new IllegalArgumentException("No review found for asset: " + assetId));
        reviewService.approve(review.id(), reviewerUserId);
        reviewRepository.setTargetType(review.id(), "ASSET");
    }

    @Transactional
    public void rejectAsset(String assetId) {
        var review = getReview(assetId)
                .orElseThrow(() -> new IllegalArgumentException("No review found for asset: " + assetId));
        reviewService.reject(review.id());
    }

    @Transactional
    public void publishAsset(String assetId) {
        var review = getReview(assetId)
                .orElseThrow(() -> new IllegalArgumentException("No review found — asset must be reviewed first"));
        if (!"APPROVED".equals(review.status())) {
            throw new IllegalStateException("Asset must be APPROVED before publishing. Current: " + review.status());
        }
        String tenantId = TenantContext.get();
        assetRepository.updatePublishStatus(tenantId, assetId, AssetPublishStatus.PUBLISHED.name());
    }

    @Transactional
    public void archiveAsset(String assetId) {
        String tenantId = TenantContext.get();
        assetRepository.updatePublishStatus(tenantId, assetId, AssetPublishStatus.ARCHIVED.name());
    }

    public Optional<AssetPublishStatus> getPublishStatus(String assetId) {
        String tenantId = TenantContext.get();
        return assetRepository.findById(tenantId, assetId)
                .map(a -> {
                    try { return AssetPublishStatus.valueOf(a.publishStatus()); }
                    catch (Exception e) { return AssetPublishStatus.DRAFT; }
                });
    }

    public boolean canPublish(String assetId) {
        var status = getPublishStatus(assetId);
        if (status.isEmpty() || status.get() != AssetPublishStatus.APPROVED) return false;
        var review = getReview(assetId);
        return review.isPresent() && "APPROVED".equals(review.get().status());
    }
}
