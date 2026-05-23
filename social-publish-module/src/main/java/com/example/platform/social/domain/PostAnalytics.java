package com.example.platform.social.domain;

import java.time.Instant;

public record PostAnalytics(
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
