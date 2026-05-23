package com.example.platform.social.api.dto;

import java.util.Map;

public record OverviewAnalyticsResponse(
        int totalPosts,
        int publishedPosts,
        int failedPosts,
        int scheduledPosts,
        Map<String, Integer> postsByPlatform,
        Map<String, Integer> statusCounts
) {}
