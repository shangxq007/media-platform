package com.example.platform.social.api;

import com.example.platform.social.api.dto.*;
import com.example.platform.social.app.PlatformAuthService;
import com.example.platform.social.app.PublishAnalyticsService;
import com.example.platform.social.app.SocialPublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/social")
@Tag(name = "Social Publish", description = "Social media publishing, scheduling, and analytics")
public class SocialPublishController {
    private static final Logger log = LoggerFactory.getLogger(SocialPublishController.class);

    private final SocialPublishService publishService;
    private final PlatformAuthService platformAuthService;
    private final PublishAnalyticsService analyticsService;

    public SocialPublishController(SocialPublishService publishService,
                                    PlatformAuthService platformAuthService,
                                    PublishAnalyticsService analyticsService) {
        this.publishService = publishService;
        this.platformAuthService = platformAuthService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/platforms")
    @Operation(summary = "List connected social platforms",
               description = "Returns all social platforms connected by the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved connected platforms"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<ConnectedPlatformResponse> getConnectedPlatforms(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId) {
        log.info("GET /api/v1/social/platforms tenant={}", tenantId);
        return platformAuthService.getConnectedPlatforms(tenantId, userId);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable String platform,
            @RequestParam(required = false) String authCode) {
        log.info("POST /api/v1/social/platforms/{}/connect tenant={}", platform, tenantId);
        return platformAuthService.connectPlatform(tenantId, userId, platform, authCode);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable String platform) {
        log.info("DELETE /api/v1/social/platforms/{} tenant={}", platform, tenantId);
        platformAuthService.disconnectPlatform(tenantId, userId, platform);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody CreatePostRequest request) {
        log.info("POST /api/v1/social/posts tenant={} platform={}", tenantId, request.platformType());
        return publishService.createPost(tenantId, userId, request);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        log.info("POST /api/v1/social/posts/{}/publish tenant={}", postId, tenantId);
        return publishService.publishNow(tenantId, userId, postId);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId,
            @Valid @RequestBody SchedulePostRequest request) {
        log.info("POST /api/v1/social/posts/{}/schedule tenant={}", postId, tenantId);
        return publishService.schedulePost(tenantId, userId, postId, request);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        log.info("DELETE /api/v1/social/posts/{}/schedule tenant={}", postId, tenantId);
        publishService.cancelScheduled(tenantId, userId, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts")
    @Operation(summary = "List posts",
               description = "Retrieve paginated list of social media posts for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved posts"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public PostHistoryResponse getPosts(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/v1/social/posts tenant={} page={} size={}", tenantId, page, size);
        return publishService.getPosts(tenantId, userId, page, size);
    }

    @GetMapping("/posts/{id}")
    @Operation(summary = "Get post details",
               description = "Retrieve details of a specific social media post")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved post"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public PublishPostResponse getPost(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        log.info("GET /api/v1/social/posts/{} tenant={}", postId, tenantId);
        return publishService.getPost(tenantId, userId, postId);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        log.info("POST /api/v1/social/posts/{}/retry tenant={}", postId, tenantId);
        return publishService.retryPost(tenantId, userId, postId);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        log.info("DELETE /api/v1/social/posts/{} tenant={}", postId, tenantId);
        publishService.deletePost(tenantId, userId, postId);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId) {
        log.info("GET /api/v1/social/drafts tenant={}", tenantId);
        return publishService.getDrafts(tenantId, userId);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody CreatePostRequest request) {
        log.info("POST /api/v1/social/drafts tenant={} platform={}", tenantId, request.platformType());
        return publishService.saveDraft(tenantId, userId, request);
    }

    @GetMapping("/analytics/overview")
    @Operation(summary = "Get analytics overview",
               description = "Retrieve overview analytics for all social media posts")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved analytics overview"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public OverviewAnalyticsResponse getOverviewAnalytics(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId) {
        log.info("GET /api/v1/social/analytics/overview tenant={}", tenantId);
        return analyticsService.getOverviewAnalytics(tenantId, userId);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable("id") String postId) {
        log.info("GET /api/v1/social/analytics/posts/{} tenant={}", postId, tenantId);
        return analyticsService.getPostAnalytics(tenantId, userId, postId);
    }
}
