package com.example.platform.web.render;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.render.infrastructure.asset.*;
import com.example.platform.render.app.timeline.*;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/dashboard")
@Tag(name = "Project Dashboard", description = "Aggregated project dashboard APIs")
public class ProjectDashboardController {

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
        throw FailClosedAuthorization.unavailable("project dashboard projection read");
    }

    @GetMapping("/activity")
    @Operation(summary = "Recent activity feed")
    public List<Map<String, Object>> activity(@PathVariable String projectId,
            @RequestParam(defaultValue = "20") int limit) {
        throw FailClosedAuthorization.unavailable("project dashboard activity projection read");
    }

    @GetMapping("/pending")
    @Operation(summary = "Pending actions requiring attention")
    public PendingDto pending(@PathVariable String projectId) {
        throw FailClosedAuthorization.unavailable("project dashboard pending projection read");
    }

    @GetMapping("/health")
    @Operation(summary = "Platform health summary")
    public HealthDto health(@PathVariable String projectId) {
        throw FailClosedAuthorization.unavailable("project dashboard health projection read");
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
