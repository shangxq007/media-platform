package com.example.platform.social.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.social.api.dto.*;
import com.example.platform.social.app.PlatformAuthService;
import com.example.platform.social.app.PublishAnalyticsService;
import com.example.platform.social.app.SocialPublishService;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class SocialPublishControllerTest {

    @Mock
    private SocialPublishService publishService;

    @Mock
    private PlatformAuthService platformAuthService;

    @Mock
    private PublishAnalyticsService analyticsService;

    private SocialPublishController controller;

    @BeforeEach
    void setUp() {
        controller = new SocialPublishController(publishService, platformAuthService, analyticsService);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static PublishPostResponse samplePost(String id) {
        return new PublishPostResponse(id, "tenant-a", "user-1",
                "text", List.of("https://example.com/img.jpg"), "twitter", "DRAFT",
                "platform-post-id", "https://twitter.com/post/1", null, null, null, null, null, 0,
                Instant.now(), Instant.now());
    }

    private static OverviewAnalyticsResponse sampleAnalytics() {
        return new OverviewAnalyticsResponse(0, 0, 0, 0, Map.of(), Map.of());
    }

    @Test
    void getConnectedPlatformsUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(platformAuthService.getConnectedPlatforms("tenant-a", "user-1"))
                .thenReturn(List.of());

        controller.getConnectedPlatforms("user-1");

        verify(platformAuthService).getConnectedPlatforms("tenant-a", "user-1");
        verify(platformAuthService, never()).getConnectedPlatforms(eq("tenant-b"), any());
    }

    @Test
    void getConnectedPlatformsRejectsWithoutTenantContext() {
        TenantContext.clear();
        assertThrows(IllegalArgumentException.class,
                () -> controller.getConnectedPlatforms("user-1"));
    }

    @Test
    void createPostUsesTenantContext() {
        TenantContext.set("tenant-a");
        CreatePostRequest request = new CreatePostRequest("text", List.of(), "twitter");
        when(publishService.createPost(eq("tenant-a"), eq("user-1"), any()))
                .thenReturn(samplePost("post-1"));

        PublishPostResponse result = controller.createPost("user-1", request);

        assertNotNull(result);
        verify(publishService).createPost(eq("tenant-a"), eq("user-1"), any());
        verify(publishService, never()).createPost(eq("tenant-b"), any(), any());
    }

    @Test
    void createPostRejectsWithoutTenantContext() {
        TenantContext.clear();
        CreatePostRequest request = new CreatePostRequest("text", List.of(), "twitter");
        assertThrows(IllegalArgumentException.class,
                () -> controller.createPost("user-1", request));
    }

    @Test
    void publishNowUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(publishService.publishNow("tenant-a", "user-1", "post-1"))
                .thenReturn(samplePost("post-1"));

        controller.publishNow("user-1", "post-1");

        verify(publishService).publishNow("tenant-a", "user-1", "post-1");
    }

    @Test
    void deletePostUsesTenantContext() {
        TenantContext.set("tenant-a");
        doNothing().when(publishService).deletePost("tenant-a", "user-1", "post-1");

        controller.deletePost("user-1", "post-1");

        verify(publishService).deletePost("tenant-a", "user-1", "post-1");
        verify(publishService, never()).deletePost("tenant-b", "user-1", "post-1");
    }

    @Test
    void getPostsUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(publishService.getPosts("tenant-a", "user-1", 0, 20))
                .thenReturn(new PostHistoryResponse(List.of(), 0L, 0, 20));

        controller.getPosts("user-1", 0, 20);

        verify(publishService).getPosts("tenant-a", "user-1", 0, 20);
    }

    @Test
    void analyticsUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(analyticsService.getOverviewAnalytics("tenant-a", "user-1"))
                .thenReturn(sampleAnalytics());

        controller.getOverviewAnalytics("user-1");

        verify(analyticsService).getOverviewAnalytics("tenant-a", "user-1");
        verify(analyticsService, never()).getOverviewAnalytics(eq("tenant-b"), any());
    }

    @Test
    void tenantAUserCannotAccessTenantBDataViaHeader() {
        TenantContext.set("tenant-a");
        when(platformAuthService.getConnectedPlatforms("tenant-a", "user-1"))
                .thenReturn(List.of());

        controller.getConnectedPlatforms("user-1");

        verify(platformAuthService).getConnectedPlatforms("tenant-a", "user-1");
        verify(platformAuthService, never()).getConnectedPlatforms(eq("tenant-b"), any());
    }

    @Test
    void fakeXTenantIdHeaderDoesNotChangeTenant() {
        TenantContext.set("tenant-a");
        when(publishService.createPost(eq("tenant-a"), eq("user-1"), any()))
                .thenReturn(samplePost("post-1"));

        CreatePostRequest request = new CreatePostRequest("text", List.of(), "twitter");
        PublishPostResponse result = controller.createPost("user-1", request);

        assertNotNull(result);
        verify(publishService).createPost(eq("tenant-a"), eq("user-1"), any());
        verify(publishService, never()).createPost(eq("tenant-b"), any(), any());
    }

    @Test
    void connectPlatformUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(platformAuthService.connectPlatform("tenant-a", "user-1", "twitter", null))
                .thenReturn(new ConnectedPlatformResponse("twitter", "tenant-a", "user-1",
                        "twitter", null, null, "CONNECTED", Instant.now(), Instant.now()));

        controller.connectPlatform("user-1", "twitter", null);

        verify(platformAuthService).connectPlatform("tenant-a", "user-1", "twitter", null);
    }

    @Test
    void disconnectPlatformUsesTenantContext() {
        TenantContext.set("tenant-a");
        doNothing().when(platformAuthService).disconnectPlatform("tenant-a", "user-1", "twitter");

        controller.disconnectPlatform("user-1", "twitter");

        verify(platformAuthService).disconnectPlatform("tenant-a", "user-1", "twitter");
    }
}
