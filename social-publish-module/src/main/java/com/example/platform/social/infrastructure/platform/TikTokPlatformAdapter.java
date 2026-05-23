package com.example.platform.social.infrastructure.platform;

import com.example.platform.social.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

// @Component disabled - stub implementation
public class TikTokPlatformAdapter implements PlatformAdapter {
    private static final Logger log = LoggerFactory.getLogger(TikTokPlatformAdapter.class);

    @Override
    public PlatformType platform() {
        return PlatformType.TIKTOK;
    }

    @Override
    public boolean validateCredentials(ConnectedPlatform platform) {
        log.info("TikTokPlatformAdapter: validating credentials for user={}", platform.platformUsername());
        return true;
    }

    @Override
    public PublishResult publish(SocialPost post, ConnectedPlatform platform) {
        log.info("TikTokPlatformAdapter: publishing post={} for user={}", post.id(), platform.platformUsername());
        String stubPostId = Ids.newId("tt");
        return new PublishResult(true, stubPostId, "https://tiktok.com/@" + platform.platformUsername() + "/video/" + stubPostId, null, null);
    }

    @Override
    public PostAnalytics fetchAnalytics(SocialPost post, ConnectedPlatform platform) {
        log.info("TikTokPlatformAdapter: fetching analytics for post={}", post.id());
        return new PostAnalytics(Ids.newId("anl"), post.id(), platform.platformType(),
                0, 0, 0, 0, 0, 0, Instant.now(), Instant.now());
    }

    @Override
    public boolean supportsMediaType(String mediaType) {
        return mediaType.equals("video/mp4");
    }

    @Override
    public int getMaxCharacters() {
        return 2200;
    }

    @Override
    public int getMaxMediaCount() {
        return 1;
    }
}
