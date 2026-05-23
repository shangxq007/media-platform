package com.example.platform.social.infrastructure.platform;

import com.example.platform.social.domain.ConnectedPlatform;
import com.example.platform.social.domain.PlatformType;
import com.example.platform.social.domain.PostAnalytics;
import com.example.platform.social.domain.SocialPost;

import java.util.List;

public interface PlatformAdapter {
    PlatformType platform();
    boolean validateCredentials(ConnectedPlatform platform);
    PublishResult publish(SocialPost post, ConnectedPlatform platform);
    PostAnalytics fetchAnalytics(SocialPost post, ConnectedPlatform platform);
    boolean supportsMediaType(String mediaType);
    int getMaxCharacters();
    int getMaxMediaCount();

    default List<String> supportedMediaTypes() {
        return List.of("image/jpeg", "image/png", "image/gif", "video/mp4");
    }
}
