package com.example.platform.federation.graphql.dto;

public record FeedbackSummary(
        int openIssues,
        int criticalIssues,
        Integer linkedRenderJobs,
        Integer linkedPromptExecutions,
        boolean replayLinked
) {}
