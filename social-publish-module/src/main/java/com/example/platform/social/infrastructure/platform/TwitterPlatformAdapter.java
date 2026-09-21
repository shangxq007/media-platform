package com.example.platform.social.infrastructure.platform;

import com.example.platform.social.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@org.springframework.context.annotation.Profile("dev & !prod")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "app.social-publish", name = "enabled", havingValue = "true")
public class TwitterPlatformAdapter implements PlatformAdapter {
    private static final Logger log = LoggerFactory.getLogger(TwitterPlatformAdapter.class);

    @Override
    public PlatformType platform() {
        return PlatformType.TWITTER;
    }

    @Override
    public boolean validateCredentials(ConnectedPlatform platform) {
        log.info("TwitterPlatformAdapter: validating credentials for user={}", platform.platformUsername());
        return false;
    }

    @Override
    public PublishResult publish(SocialPost post, ConnectedPlatform platform) {
        log.info("TwitterPlatformAdapter: publishing post={} for user={}", post.id(), platform.platformUsername());
        return new PublishResult(false, null, null, "DEVELOPMENT_STUB", "Development adapter cannot publish");
    }

    @Override
    public PostAnalytics fetchAnalytics(SocialPost post, ConnectedPlatform platform) {
        log.info("TwitterPlatformAdapter: fetching analytics for post={}", post.id());
        throw new UnsupportedOperationException("Development adapter cannot fetch provider analytics");
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
