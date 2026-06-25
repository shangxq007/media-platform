package com.example.platform.web.render;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.outbox.app.PlatformJobRepository;
import com.example.platform.render.infrastructure.asset.*;
import com.example.platform.render.app.timeline.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/dashboard")
@Tag(name = "Project Dashboard", description = "Aggregated project dashboard APIs")
public class ProjectDashboardController {

    private static final Logger log = LoggerFactory.getLogger(ProjectDashboardController.class);
    private final AssetRepository assetRepo;
    private final MarketplaceListingRepository marketplaceRepo;
    private final SearchProjectionRepository searchProjectionRepo;
    private final TimelineRevisionRepository revisionRepo;
    private final TimelineReviewRepository reviewRepo;
    private final OutboxEventService outboxService;

    public ProjectDashboardController(AssetRepository assetRepo,
                                        MarketplaceListingRepository marketplaceRepo,
                                        SearchProjectionRepository searchProjectionRepo,
                                        TimelineRevisionRepository revisionRepo,
                                        TimelineReviewRepository reviewRepo,
                                        OutboxEventService outboxService) {
        this.assetRepo = assetRepo;
        this.marketplaceRepo = marketplaceRepo;
        this.searchProjectionRepo = searchProjectionRepo;
        this.revisionRepo = revisionRepo;
        this.reviewRepo = reviewRepo;
        this.outboxService = outboxService;
    }

    @GetMapping
    @Operation(summary = "Full project dashboard summary")
    public ResponseEntity<DashboardDto> dashboard(@PathVariable String projectId,
            @RequestParam(required = false, defaultValue = "tenant_1") String tenantId) {
        long start = System.currentTimeMillis();

        var assets = assetRepo.listByProject(tenantId, projectId);
        int publishedAssets = (int) assets.stream().filter(a -> "PUBLISHED".equals(a.publishStatus())).count();
        int draftAssets = (int) assets.stream().filter(a -> "DRAFT".equals(a.publishStatus())).count();

        var marketplace = marketplaceRepo.listByStatus("PUBLISHED", 500);
        int totalListings = marketplace.size();
        int publishedListings = (int) marketplace.stream()
                .filter(m -> m.status() != null && m.status().name().equals("PUBLISHED")).count();

        var reviews = reviewRepo.listByProject(projectId, 200);
        int openReviews = (int) reviews.stream().filter(r -> "OPEN".equals(r.status())).count();
        int approvedReviews = (int) reviews.stream().filter(r -> "APPROVED".equals(r.status())).count();

        var outboxOverview = outboxService.overview();
        int pendingEvents = outboxOverview.get("pending") != null
                ? Integer.parseInt(outboxOverview.get("pending").toString()) : 0;
        int failedEvents = outboxOverview.get("failed") != null
                ? Integer.parseInt(outboxOverview.get("failed").toString()) : 0;

        var dto = new DashboardDto(projectId,
                new AssetSummaryDto(assets.size(), publishedAssets, draftAssets),
                new TimelineSummaryDto(0, 0, 0), // timeline stats deferred
                new ReviewSummaryDto(reviews.size(), openReviews, approvedReviews),
                new MarketplaceSummaryDto(totalListings, publishedListings),
                new PlatformHealthDto(pendingEvents, failedEvents, 0));
        log.info("Dashboard loaded: project={} assets={} listings={} reviews={} latency={}ms",
                projectId, assets.size(), totalListings, reviews.size(), System.currentTimeMillis() - start);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/activity")
    @Operation(summary = "Recent activity feed")
    public List<Map<String, Object>> activity(@PathVariable String projectId,
            @RequestParam(defaultValue = "20") int limit) {
        return outboxService.recent(limit);
    }

    @GetMapping("/pending")
    @Operation(summary = "Pending actions requiring attention")
    public PendingDto pending(@PathVariable String projectId) {
        var reviews = reviewRepo.listByProject(projectId, 200);
        int pendingReviews = (int) reviews.stream().filter(r -> "OPEN".equals(r.status())).count();
        int pendingChanges = (int) reviews.stream().filter(r -> "CHANGES_REQUESTED".equals(r.status())).count();

        var outboxOverview = outboxService.overview();
        int pendingEvents = outboxOverview.get("pending") != null
                ? Integer.parseInt(outboxOverview.get("pending").toString()) : 0;

        return new PendingDto(pendingReviews + pendingChanges, pendingChanges,
                pendingEvents > 0 ? 1 : 0, 0, 0, 0);
    }

    @GetMapping("/health")
    @Operation(summary = "Platform health summary")
    public HealthDto health(@PathVariable String projectId) {
        var overview = outboxService.overview();
        int pending = overview.get("pending") != null ? Integer.parseInt(overview.get("pending").toString()) : 0;
        int failed = overview.get("failed") != null ? Integer.parseInt(overview.get("failed").toString()) : 0;
        int deadLetter = overview.get("deadLetter") != null ? Integer.parseInt(overview.get("deadLetter").toString()) : 0;
        return new HealthDto(0, 0, failed, pending, deadLetter);
    }

    public record DashboardDto(String projectId, AssetSummaryDto assets,
            TimelineSummaryDto timeline, ReviewSummaryDto reviews,
            MarketplaceSummaryDto marketplace, PlatformHealthDto health) {}

    public record AssetSummaryDto(int total, int published, int drafts) {}
    public record TimelineSummaryDto(int count, int revisionCount, int mergeCount) {}
    public record ReviewSummaryDto(int total, int open, int approved) {}
    public record MarketplaceSummaryDto(int total, int published) {}
    public record PlatformHealthDto(int pendingEvents, int failedEvents, int runningJobs) {}
    public record PendingDto(int pendingReviews, int pendingChanges, int pendingMerges,
            int pendingPublishes, int failedEnrichment, int failedReindex) {}
    public record HealthDto(int pendingJobs, int runningJobs, int failedJobs,
            int pendingEvents, int deadLetterEvents) {}
}
