package com.example.platform.social.domain;

import java.time.Instant;
import java.util.List;

public record SocialPost(
        String id,
        String tenantId,
        String userId,
        String projectId,
        String connectedPlatformId,
        Long connectedPlatformBindingVersion,
        String artifactId,
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
) {
    public SocialPost {
        boolean anyBinding = projectId != null
                || connectedPlatformId != null
                || connectedPlatformBindingVersion != null;
        boolean completeBinding = projectId != null
                && connectedPlatformId != null
                && connectedPlatformBindingVersion != null;
        if (anyBinding != completeBinding) {
            throw new IllegalArgumentException(
                    "projectId, connectedPlatformId, and bindingVersion must be all present or all absent");
        }
        if (connectedPlatformBindingVersion != null && connectedPlatformBindingVersion < 1) {
            throw new IllegalArgumentException("connectedPlatformBindingVersion must be positive");
        }
    }
}
