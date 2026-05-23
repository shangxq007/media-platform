package com.example.platform.social.infrastructure.platform;

import com.example.platform.social.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

// @Component disabled - stub implementation
public class LinkedInPlatformAdapter implements PlatformAdapter {
    private static final Logger log = LoggerFactory.getLogger(LinkedInPlatformAdapter.class);

    @Override
    public PlatformType platform() {
        return PlatformType.LINKEDIN;
    }

    @Override
    public boolean validateCredentials(ConnectedPlatform platform) {
        log.info("LinkedInPlatformAdapter: validating credentials for user={}", platform.platformUsername());
        return true;
    }

    @Override
    public PublishResult publish(SocialPost post, ConnectedPlatform platform) {
        log.info("LinkedInPlatformAdapter: publishing post={} for user={}", post.id(), platform.platformUsername());
        String stubPostId = Ids.newId("li");
        return new PublishResult(true, stubPostId, "https://linkedin.com/feed/update/" + stubPostId, null, null);
    }

    @Override
    public PostAnalytics fetchAnalytics(SocialPost post, ConnectedPlatform platform) {
        log.info("LinkedInPlatformAdapter: fetching analytics for post={}", post.id());
        return new PostAnalytics(Ids.newId("anl"), post.id(), platform.platformType(),
                0, 0, 0, 0, 0, 0, Instant.now(), Instant.now());
    }

    @Override
    public boolean supportsMediaType(String mediaType) {
        return mediaType.startsWith("image/") || mediaType.equals("video/mp4") || mediaType.equals("application/pdf");
    }

    @Override
    public int getMaxCharacters() {
        return 3000;
    }

    @Override
    public int getMaxMediaCount() {
        return 9;
    }
}
