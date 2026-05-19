package com.example.platform.federation.graphql.dto;

public record TimelineSummary(
        double durationSeconds,
        int tracks,
        int clips,
        int subtitles,
        int effects
) {}
