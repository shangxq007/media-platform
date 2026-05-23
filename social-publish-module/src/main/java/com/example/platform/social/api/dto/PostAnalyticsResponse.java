package com.example.platform.social.api.dto;

import java.time.Instant;

public record PostAnalyticsResponse(
        String id,
        String postId,
        String platformType,
        int impressions,
        int reach,
        int likes,
        int comments,
        int shares,
        int clicks,
        Instant fetchedAt,
        Instant createdAt
) {}
