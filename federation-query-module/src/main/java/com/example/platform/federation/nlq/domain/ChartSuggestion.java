package com.example.platform.federation.nlq.domain;

public record ChartSuggestion(
    String chartType,
    String xField,
    String yField,
    String groupField,
    String title,
    String reason
) {}
