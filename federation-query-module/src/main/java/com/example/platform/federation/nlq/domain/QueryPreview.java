package com.example.platform.federation.nlq.domain;

import java.util.List;
import java.util.Map;

public record QueryPreview(
    String previewId,
    String question,
    String intent,
    List<String> datasets,
    String sqlDraft,
    String sqlExplanation,
    Map<String, Object> parameters,
    SqlSafetyResult safety,
    String accessDecision,
    String riskLevel,
    boolean requiresConfirmation,
    boolean requiresReview,
    List<String> chartSuggestions,
    List<String> warnings,
    String traceId
) {}
