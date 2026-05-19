package com.example.platform.federation.graphql.dto;

public record MonitoringFeedbackOverview(
        MonitoringStatus monitoringStatus,
        FeedbackSummary feedbackSummary,
        ProblematicDataSummary problematicDataSummary
) {}
