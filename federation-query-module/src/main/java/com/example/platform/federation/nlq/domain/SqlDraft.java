package com.example.platform.federation.nlq.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SqlDraft(
    String draftId,
    String question,
    String intent,
    List<String> datasetKeys,
    String sql,
    Map<String, Object> parameters,
    List<String> assumptions,
    List<String> requiredPermissions,
    String riskLevel,
    String explanation,
    List<String> chartSuggestions,
    double confidence,
    Instant createdAt
) {}
