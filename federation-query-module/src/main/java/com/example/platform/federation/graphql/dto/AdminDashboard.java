package com.example.platform.federation.graphql.dto;

import java.util.List;

public record AdminDashboard(
        RenderStats renderStats,
        List<ProviderHealth> providerHealth,
        AdminBillingSummary billingSummary,
        FeedbackSummary feedbackSummary,
        ExtensionSummary extensionSummary
) {}
