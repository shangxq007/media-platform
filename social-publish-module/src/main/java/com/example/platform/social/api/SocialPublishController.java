package com.example.platform.social.api;

import com.example.platform.social.api.dto.*;
import com.example.platform.social.app.PlatformAuthService;
import com.example.platform.social.app.PublishAnalyticsService;
import com.example.platform.social.app.SocialPublishService;
import com.example.platform.social.app.SocialPostReadService;
import com.example.platform.social.app.SocialAccountReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/social")
@Tag(name = "Social Publish", description = "Social media publishing, scheduling, and analytics")
public class SocialPublishController {
    private static final Logger log = LoggerFactory.getLogger(SocialPublishController.class);

    private final SocialPublishService publishService;
    private final PlatformAuthService platformAuthService;
    private final PublishAnalyticsService analyticsService;
    private final SocialPostReadService readService;
    private final SocialAccountReadService accountReadService;

    public SocialPublishController(@org.springframework.lang.Nullable SocialPublishService publishService,
                                    @org.springframework.lang.Nullable PlatformAuthService platformAuthService,
                                    @org.springframework.lang.Nullable PublishAnalyticsService analyticsService,
                                    SocialPostReadService readService,
                                    SocialAccountReadService accountReadService) {
        this.publishService = publishService;
        this.platformAuthService = platformAuthService;
        this.analyticsService = analyticsService;
        this.readService = readService;
        this.accountReadService = accountReadService;
    }

    private static <T> T requirePublishing(T service) {
        if (service == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Social publishing is disabled");
        }
        return service;
    }

    private static String requireTenantId() {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        return tenantId;
    }

    @GetMapping("/platforms")
    @Operation(summary = "List connected social platforms",
               description = "Returns all social platforms connected by the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved connected platforms"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<PublicationAccountResponse> getConnectedPlatforms(
            @RequestParam String projectId) {
        String tenantId = requireTenantId();
        log.info("GET /api/social/platforms tenant={} project={}", tenantId, projectId);
        return accountReadService.list(tenantId, projectId);
    }

    @PostMapping("/platforms/{platform}/connect")
    @Operation(summary = "Connect a social platform",
               description = "Initiate OAuth flow or connect a social media platform account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Platform connected successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid platform or auth code"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ConnectedPlatformResponse connectPlatform(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable String platform,
            @RequestParam(required = false) String authCode) {
        String tenantId = requireTenantId();
        log.info("POST /api/social/platforms/{}/connect tenant={}", platform, tenantId);
        return requirePublishing(platformAuthService).connectPlatform(tenantId, userId, platform, authCode);
    }

    @DeleteMapping("/platforms/{platform}")
    @Operation(summary = "Disconnect a social platform",
               description = "Remove a connected social media platform account")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Platform disconnected successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Platform not found")
    })
    public ResponseEntity<Void> disconnectPlatform(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable String platform) {
        String tenantId = requireTenantId();
        log.info("DELETE /api/social/platforms/{} tenant={}", platform, tenantId);
        requirePublishing(platformAuthService).disconnectPlatform(tenantId, userId, platform);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts")
    @Operation(summary = "Create a social post",
               description = "Create a new social media post draft or scheduled post")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Post created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public PublishPostResponse createPost(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody CreatePostRequest request) {
        String tenantId = requireTenantId();
        log.info("POST /api/social/posts tenant={} platform={}", tenantId, request.platformType());
        return requirePublishing(publishService).createPost(tenantId, userId, request);
    }

    @PostMapping("/posts/{id}/publish")
    @Operation(summary = "Publish a post immediately",
               description = "Publish an existing post to the connected social platform right away")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Post published successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public PublishPostResponse publishNow(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        String tenantId = requireTenantId();
        log.info("POST /api/social/posts/{}/publish tenant={}", postId, tenantId);
        return requirePublishing(publishService).publishNow(tenantId, userId, postId);
    }

    @PostMapping("/posts/{id}/schedule")
    @Operation(summary = "Schedule a post",
               description = "Schedule an existing post for future publishing")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Post scheduled successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid schedule request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public PublishPostResponse schedulePost(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId,
            @Valid @RequestBody SchedulePostRequest request) {
        String tenantId = requireTenantId();
        log.info("POST /api/social/posts/{}/schedule tenant={}", postId, tenantId);
        return requirePublishing(publishService).schedulePost(tenantId, userId, postId, request);
    }

    @DeleteMapping("/posts/{id}/schedule")
    @Operation(summary = "Cancel a scheduled post",
               description = "Cancel a previously scheduled post")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Scheduled post cancelled successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public ResponseEntity<Void> cancelScheduled(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        String tenantId = requireTenantId();
        log.info("DELETE /api/social/posts/{}/schedule tenant={}", postId, tenantId);
        requirePublishing(publishService).cancelScheduled(tenantId, userId, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts")
    @Operation(summary = "List publication records",
               description = "Returns explicitly bound local records whose planned publish time is in [start,end)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved posts"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public PublicationPostListResponse getPosts(
            @RequestParam String projectId,
            @RequestParam String connectedAccountId,
            @RequestParam long bindingVersion,
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam(defaultValue = "50") int limit) {
        String tenantId = requireTenantId();
        log.info("GET /api/social/posts tenant={} project={}", tenantId, projectId);
        return readService.list(
                tenantId, projectId, connectedAccountId, bindingVersion, start, end, limit);
    }

    @GetMapping("/posts/{id}")
    @Operation(summary = "Get publication record",
               description = "Returns one safe local source snapshot for an exact Project/account binding")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved post"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public PublicationPostResponse getPost(
            @PathVariable("id") String postId,
            @RequestParam String projectId,
            @RequestParam String connectedAccountId,
            @RequestParam long bindingVersion) {
        String tenantId = requireTenantId();
        log.info("GET /api/social/posts/{} tenant={}", postId, tenantId);
        return readService.get(
                tenantId, projectId, connectedAccountId, bindingVersion, postId);
    }

    @PostMapping("/posts/{id}/retry")
    @Operation(summary = "Retry a failed post",
               description = "Retry publishing a post that previously failed")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Post retry initiated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public PublishPostResponse retryPost(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        String tenantId = requireTenantId();
        log.info("POST /api/social/posts/{}/retry tenant={}", postId, tenantId);
        return requirePublishing(publishService).retryPost(tenantId, userId, postId);
    }

    @DeleteMapping("/posts/{id}")
    @Operation(summary = "Delete a post",
               description = "Delete a social media post")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Post deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public ResponseEntity<Void> deletePost(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        String tenantId = requireTenantId();
        log.info("DELETE /api/social/posts/{} tenant={}", postId, tenantId);
        requirePublishing(publishService).deletePost(tenantId, userId, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/drafts")
    @Operation(summary = "List drafts",
               description = "Retrieve all draft posts for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved drafts"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<PublishPostResponse> getDrafts(
            @RequestHeader("X-User-ID") String userId) {
        String tenantId = requireTenantId();
        log.info("GET /api/social/drafts tenant={}", tenantId);
        return requirePublishing(publishService).getDrafts(tenantId, userId);
    }

    @PostMapping("/drafts")
    @Operation(summary = "Save a draft",
               description = "Save a social media post as a draft")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Draft saved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public PublishPostResponse saveDraft(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody CreatePostRequest request) {
        String tenantId = requireTenantId();
        log.info("POST /api/social/drafts tenant={} platform={}", tenantId, request.platformType());
        return requirePublishing(publishService).saveDraft(tenantId, userId, request);
    }

    @GetMapping("/analytics/overview")
    @Operation(summary = "Get analytics overview",
               description = "Retrieve overview analytics for all social media posts")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved analytics overview"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public OverviewAnalyticsResponse getOverviewAnalytics(
            @RequestHeader("X-User-ID") String userId) {
        String tenantId = requireTenantId();
        log.info("GET /api/social/analytics/overview tenant={}", tenantId);
        return requirePublishing(analyticsService).getOverviewAnalytics(tenantId, userId);
    }

    @GetMapping("/analytics/posts/{id}")
    @Operation(summary = "Get post analytics",
               description = "Retrieve analytics for a specific social media post")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved post analytics"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public PostAnalyticsResponse getPostAnalytics(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        String tenantId = requireTenantId();
        log.info("GET /api/social/analytics/posts/{} tenant={}", postId, tenantId);
        return requirePublishing(analyticsService).getPostAnalytics(tenantId, userId, postId);
    }
}
