package com.example.platform.social.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.shared.web.TenantContext;
import com.example.platform.social.api.dto.CreatePostRequest;
import com.example.platform.social.api.dto.PublicationPostListResponse;
import com.example.platform.social.api.dto.PublicationPostResponse;
import com.example.platform.social.api.dto.PublishPostResponse;
import com.example.platform.social.app.PlatformAuthService;
import com.example.platform.social.app.PublishAnalyticsService;
import com.example.platform.social.app.SocialAccountReadService;
import com.example.platform.social.app.SocialPostReadService;
import com.example.platform.social.app.SocialPublishService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialPublishControllerTest {

    @Mock private SocialPublishService publishService;
    @Mock private PlatformAuthService platformAuthService;
    @Mock private PublishAnalyticsService analyticsService;
    @Mock private SocialPostReadService readService;
    @Mock private SocialAccountReadService accountReadService;

    private SocialPublishController controller;

    @BeforeEach
    void setUp() {
        controller = new SocialPublishController(
                publishService, platformAuthService, analyticsService, readService, accountReadService);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void accountQueryUsesProjectScopedCanonicalReadServiceWithoutUserHeader() {
        TenantContext.set("tenant-a");
        when(accountReadService.list("tenant-a", "project-1")).thenReturn(List.of());

        assertEquals(List.of(), controller.getConnectedPlatforms("project-1"));

        verify(accountReadService).list("tenant-a", "project-1");
        verifyNoInteractions(platformAuthService);
    }

    @Test
    void listAndDetailUseSafeReadServiceWithoutMutationOrProviderServices() {
        TenantContext.set("tenant-a");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");
        PublicationPostListResponse list = new PublicationPostListResponse(
                List.of(), PublicationPostListResponse.Coverage.BOUNDED_PARTIAL);
        PublicationPostResponse detail = response("post-1", start);
        when(readService.list("tenant-a", "project-1", "account-1", 7L, start, end, 20))
                .thenReturn(list);
        when(readService.get("tenant-a", "project-1", "account-1", 7L, "post-1"))
                .thenReturn(detail);

        assertEquals(list, controller.getPosts("project-1", "account-1", 7L, start, end, 20));
        assertEquals(detail, controller.getPost("post-1", "project-1", "account-1", 7L));

        verifyNoInteractions(publishService, platformAuthService, analyticsService, accountReadService);
    }

    @Test
    void preexistingPostMutationContractRemainsAndDoesNotCreateBinding() {
        TenantContext.set("tenant-a");
        CreatePostRequest request = new CreatePostRequest("text", List.of(), "YOUTUBE");
        when(publishService.createPost("tenant-a", "legacy-user", request))
                .thenReturn(samplePost("post-1"));

        assertNotNull(controller.createPost("legacy-user", request));

        verify(publishService).createPost("tenant-a", "legacy-user", request);
        verifyNoInteractions(readService, accountReadService);
    }

    @Test
    void missingTenantStopsBeforeAnyService() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.getPosts("project-1", "account-1", 7L, Instant.EPOCH, Instant.MAX, 20));
        verifyNoInteractions(readService, accountReadService, publishService);
    }

    @Test
    void forgedUserHeaderIsNotAnInputToReadRoutes() throws Exception {
        assertEquals(6, SocialPublishController.class.getMethod(
                "getPosts", String.class, String.class, long.class, Instant.class, Instant.class, int.class)
                .getParameterCount());
        assertEquals(1, SocialPublishController.class.getMethod(
                "getConnectedPlatforms", String.class).getParameterCount());
        verify(publishService, never()).getDrafts("tenant-a", "forged-user");
    }

    private static PublicationPostResponse response(String id, Instant scheduledAt) {
        return new PublicationPostResponse(
                id, "project-1", "account-1", 7L,
                "text", PublicationPostResponse.ContentAvailability.AVAILABLE,
                PublicationPostResponse.ContentVersionRelationState.NOT_PROVIDED,
                "artifact-1", PublicationPostResponse.ArtifactRelationState.AVAILABLE,
                "YOUTUBE", scheduledAt,
                PublicationPostResponse.TimeMeaning.PLANNED_PUBLISH_TIME,
                PublicationPostResponse.TimePrecision.EXACT_INSTANT,
                PublicationPostResponse.SourceVerification.VERIFIED_LOCAL_RECORD,
                PublicationPostResponse.EndpointAccess.AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ,
                PublicationPostResponse.GlobalEffectiveAccess.UNKNOWN_FAIL_CLOSED);
    }

    private static PublishPostResponse samplePost(String id) {
        return new PublishPostResponse(
                id, "tenant-a", "legacy-user", "text", List.of(), "YOUTUBE", "DRAFT",
                null, null, null, null, null, null, null, 0, Instant.EPOCH, Instant.EPOCH);
    }
}
