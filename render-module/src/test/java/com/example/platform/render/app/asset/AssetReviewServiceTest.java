package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.timeline.TimelineReviewRepository;
import com.example.platform.render.app.timeline.TimelineReviewService;
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
    private TimelineReviewService reviewService;
    private TimelineReviewRepository reviewRepository;
    private AssetReviewService assetReviewService;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant_1");
        assetRepository = mock(AssetRepository.class);
        reviewService = mock(TimelineReviewService.class);
        reviewRepository = mock(TimelineReviewRepository.class);
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

        when(reviewRepository.findById(any())).thenReturn(Optional.of(
                new TimelineReviewRepository.ReviewRow("arev_1", "proj_1", "tenant_1", "asset_1",
                        "user_1", "Review Asset", "desc", "OPEN",
                        OffsetDateTime.now(), OffsetDateTime.now())));

        var result = assetReviewService.submitForReview("asset_1", "user_1", "Review Asset", "desc");

        assertNotNull(result);
        assertEquals("OPEN", result.status());
    }

    private com.example.platform.render.domain.asset.Asset makeAsset(String id) {
        return new com.example.platform.render.domain.asset.Asset(
                id, "tenant_1", "proj_1", "key", "VIDEO", "f.mp4",
                100L, null, null, null, null, "v1", null, null, null, null,
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
