package com.example.platform.social.api.dto;

import java.time.Instant;
import java.util.List;

public record PublishPostResponse(
        String id,
        String tenantId,
        String userId,
        String contentText,
        List<String> mediaUrls,
        String platformType,
        String status,
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
