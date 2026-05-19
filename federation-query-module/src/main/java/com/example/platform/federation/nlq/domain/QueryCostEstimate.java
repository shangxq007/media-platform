package com.example.platform.federation.nlq.domain;

public record QueryCostEstimate(
    int datasetCount,
    int daysRange,
    int limit,
    boolean hasGroupBy,
    boolean hasOrderBy,
    boolean hasJoin,
    String riskLevel,
    boolean requiresReview
) {}
