package com.example.platform.social.infrastructure.platform;

import com.example.platform.social.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

//@Component disabled - conflicts with real adapters
public class StubPlatformAdapter implements PlatformAdapter {
    private static final Logger log = LoggerFactory.getLogger(StubPlatformAdapter.class);

    private final Map<PlatformType, PlatformConfig> configs = Map.of(
            PlatformType.TWITTER, new PlatformConfig(280, 4),
            PlatformType.INSTAGRAM, new PlatformConfig(2200, 10),
            PlatformType.FACEBOOK, new PlatformConfig(63206, 10),
            PlatformType.LINKEDIN, new PlatformConfig(3000, 9),
            PlatformType.TIKTOK, new PlatformConfig(2200, 1),
            PlatformType.YOUTUBE, new PlatformConfig(5000, 1),
            PlatformType.PINTEREST, new PlatformConfig(500, 1),
            PlatformType.THREADS, new PlatformConfig(500, 10),
            PlatformType.MASTODON, new PlatformConfig(500, 4),
            PlatformType.BLUESKY, new PlatformConfig(300, 4)
    );

    @Override
    public PlatformType platform() {
        return PlatformType.TWITTER;
    }

    @Override
    public boolean validateCredentials(ConnectedPlatform platform) {
        log.info("StubPlatformAdapter: validating credentials for platform={}", platform.platformType());
        return true;
    }

    @Override
    public PublishResult publish(SocialPost post, ConnectedPlatform platform) {
        log.info("StubPlatformAdapter: publishing post={} to platform={}", post.id(), platform.platformType());
        String stubPostId = Ids.newId("stub");
        String stubUrl = "https://stub.example.com/post/" + stubPostId;
        return new PublishResult(true, stubPostId, stubUrl, null, null);
    }

    @Override
    public PostAnalytics fetchAnalytics(SocialPost post, ConnectedPlatform platform) {
        log.info("StubPlatformAdapter: fetching analytics for post={}", post.id());
        return new PostAnalytics(
                Ids.newId("anl"),
                post.id(),
                platform.platformType(),
                0, 0, 0, 0, 0, 0,
                Instant.now(),
                Instant.now()
        );
    }

    @Override
    public boolean supportsMediaType(String mediaType) {
        return true;
    }

    @Override
    public int getMaxCharacters() {
        return 280;
    }

    @Override
    public int getMaxMediaCount() {
        return 4;
    }

    public PlatformConfig getConfig(PlatformType type) {
        return configs.getOrDefault(type, new PlatformConfig(280, 4));
    }

    public int getMaxCharactersFor(PlatformType type) {
        PlatformConfig cfg = configs.get(type);
        return cfg != null ? cfg.maxChars() : 280;
    }

    public int getMaxMediaFor(PlatformType type) {
        PlatformConfig cfg = configs.get(type);
        return cfg != null ? cfg.maxMedia() : 4;
    }

    record PlatformConfig(int maxChars, int maxMedia) {}
}
