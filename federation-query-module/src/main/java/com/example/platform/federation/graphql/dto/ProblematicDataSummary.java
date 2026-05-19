package com.example.platform.federation.graphql.dto;

public record ProblematicDataSummary(
        int total,
        int requireReview,
        int autoFixed,
        int critical
) {}
