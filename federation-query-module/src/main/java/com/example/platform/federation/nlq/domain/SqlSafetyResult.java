package com.example.platform.federation.nlq.domain;

import java.util.List;

public record SqlSafetyResult(
    boolean safe,
    List<String> violations,
    String normalizedSql,
    List<String> referencedDatasets,
    List<String> referencedFields,
    String estimatedRisk,
    boolean requiresReview
) {}
