package com.example.platform.social.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.social.domain.PlatformType;
import com.example.platform.social.domain.PostStatus;
import com.example.platform.social.domain.SocialPlatformProviderUnavailableException;
import com.example.platform.social.domain.SocialPost;
import com.example.platform.social.infrastructure.persistence.ConnectedPlatformRepository;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SocialProviderContainmentTest {

    @Test
    void fakePlatformAdaptersDoNotExistInProductionSourceSet() {
        for (String fake : List.of(
                "TwitterPlatformAdapter.java",
                "InstagramPlatformAdapter.java",
                "LinkedInPlatformAdapter.java",
                "TikTokPlatformAdapter.java",
                "StubPlatformAdapter.java")) {
            assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(
                    "src/main/java/com/example/platform/social/infrastructure/platform", fake)));
        }
    }

    @Test
    void connectReturnsTypedUnsupportedWithoutPersistingOrExecutingProvider() {
        ConnectedPlatformRepository platforms = mock(ConnectedPlatformRepository.class);
        PlatformAuthService service = new PlatformAuthService(platforms);

        SocialPlatformProviderUnavailableException failure = assertThrows(
                SocialPlatformProviderUnavailableException.class,
                () -> service.connectPlatform("tenant-1", "user-1", "TWITTER", "auth-code"));

        assertEquals("SOCIAL-501-001", failure.getErrorCode().code());
        assertEquals(501, failure.getErrorCode().status());
        verifyNoInteractions(platforms);
    }

    @Test
    void publishWithoutRealProviderCannotPersistPublishedSuccess() {
        SocialPostRepository posts = mock(SocialPostRepository.class);
        ConnectedPlatformRepository platforms = mock(ConnectedPlatformRepository.class);
        SocialPost draft = new SocialPost(
                "post-1", "tenant-1", "user-1", "content", List.of(),
                PlatformType.TWITTER, PostStatus.DRAFT,
                null, null, null, null, null, null, null, 0,
                Instant.EPOCH, Instant.EPOCH);
        when(posts.findById("post-1")).thenReturn(Optional.of(draft));
        SocialPublishService service = new SocialPublishService(posts, platforms, List.of());

        SocialPlatformProviderUnavailableException failure = assertThrows(
                SocialPlatformProviderUnavailableException.class,
                () -> service.publishNow("tenant-1", "user-1", "post-1"));

        assertEquals("SOCIAL-501-001", failure.getErrorCode().code());
        verify(posts, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(platforms);
    }
}
