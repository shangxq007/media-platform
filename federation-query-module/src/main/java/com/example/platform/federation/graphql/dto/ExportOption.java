package com.example.platform.federation.graphql.dto;

import java.util.List;

public record ExportOption(
        String preset,
        boolean allowed,
        String reasonCode,
        MoneyDto estimatedCost,
        String recommendedPreset,
        List<String> providerCandidates,
        Double quotaRemaining,
        boolean requiresReview
) {}
