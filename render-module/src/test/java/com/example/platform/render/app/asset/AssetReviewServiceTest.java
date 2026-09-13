package com.example.platform.render.app.asset;

import com.example.platform.timeline.api.review.ReviewRecords;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.timeline.api.review.ReviewQueries;
import com.example.platform.timeline.api.review.TimelineReviews;
import com.example.platform.render.domain.asset.AssetPublishStatus;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import java.time.OffsetDateTime;
import java.util.*;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetReviewServiceTest {

    private AssetRepository assetRepository;
    private TimelineReviews reviewService;
    private ReviewQueries reviewRepository;
    private AssetReviewService assetReviewService;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant_1");
        assetRepository = mock(AssetRepository.class);
        reviewService = mock(TimelineReviews.class);
        reviewRepository = mock(ReviewQueries.class);
        assetReviewService = new AssetReviewService(assetRepository, reviewService, reviewRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSubmitForReview() {
        when(assetRepository.findById(any(), eq("asset_1")))
                .thenReturn(Optional.of(makeAsset("asset_1")));

        when(reviewService.createAssetReview("proj_1","asset_1","user_1","Review Asset","desc")).thenReturn(
                new ReviewRecords.ReviewRow("arev_1", "proj_1", "tenant_1", "asset_1",
                        "user_1", "Review Asset", "desc", "OPEN",
                        OffsetDateTime.now(), OffsetDateTime.now()));

        var result = assetReviewService.submitForReview("asset_1", "user_1", "Review Asset", "desc");

        assertNotNull(result);
        assertEquals("OPEN", result.status());
        verify(reviewService).createAssetReview("proj_1","asset_1","user_1","Review Asset","desc");
        verifyNoInteractions(reviewRepository);
    }

    private com.example.platform.render.domain.asset.Asset makeAsset(String id) {
        return new com.example.platform.render.domain.asset.Asset(
                id, "tenant_1", "proj_1", "key", "VIDEO", "f.mp4",
                100L, null, "v1", null, null, null, null,
                null, null, false, false, "DRAFT", java.time.Instant.now(), java.time.Instant.now());
    }

    @Test
    void shouldRejectPublishWhenNotApproved() {
        when(assetRepository.findById(any(), eq("asset_1")))
                .thenReturn(Optional.of(makeAsset("asset_1")));
        when(reviewRepository.findByTargetId("asset_1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> assetReviewService.publishAsset("asset_1"));
    }

    @Test
    void shouldCheckPublishStatus() {
        when(assetRepository.findById(any(), eq("asset_1")))
                .thenReturn(Optional.of(makeAsset("asset_1")));

        var status = assetReviewService.getPublishStatus("asset_1");
        assertTrue(status.isPresent());
        assertEquals(AssetPublishStatus.DRAFT, status.get());
    }
}
