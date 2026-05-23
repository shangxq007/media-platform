package com.example.platform.social.domain;

import java.time.Instant;
import java.util.List;

public record SocialPost(
        String id,
        String tenantId,
        String userId,
        String contentText,
        List<String> mediaUrls,
        PlatformType platformType,
        PostStatus status,
        String platformPostId,
        String platformPostUrl,
        Instant scheduledAt,
        Instant publishedAt,
        Instant failedAt,
        String errorCode,
        String errorMessage,
        int retryCount,
        Instant createdAt,
        Instant updatedAt
) {}
