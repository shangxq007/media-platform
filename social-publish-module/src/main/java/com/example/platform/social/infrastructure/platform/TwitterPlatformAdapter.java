package com.example.platform.social.infrastructure.platform;

import com.example.platform.social.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TwitterPlatformAdapter implements PlatformAdapter {
    private static final Logger log = LoggerFactory.getLogger(TwitterPlatformAdapter.class);

    @Override
    public PlatformType platform() {
        return PlatformType.TWITTER;
    }

    @Override
    public boolean validateCredentials(ConnectedPlatform platform) {
        log.info("TwitterPlatformAdapter: validating credentials for user={}", platform.platformUsername());
        return true;
    }

    @Override
    public PublishResult publish(SocialPost post, ConnectedPlatform platform) {
        log.info("TwitterPlatformAdapter: publishing post={} for user={}", post.id(), platform.platformUsername());
        String stubPostId = Ids.newId("tw");
        return new PublishResult(true, stubPostId, "https://twitter.com/" + platform.platformUsername() + "/status/" + stubPostId, null, null);
    }

    @Override
    public PostAnalytics fetchAnalytics(SocialPost post, ConnectedPlatform platform) {
        log.info("TwitterPlatformAdapter: fetching analytics for post={}", post.id());
        return new PostAnalytics(Ids.newId("anl"), post.id(), platform.platformType(),
                0, 0, 0, 0, 0, 0, Instant.now(), Instant.now());
    }

    @Override
    public boolean supportsMediaType(String mediaType) {
        return mediaType.startsWith("image/") || mediaType.equals("video/mp4");
    }

    @Override
    public int getMaxCharacters() {
        return 280;
    }

    @Override
    public int getMaxMediaCount() {
        return 4;
    }
}
