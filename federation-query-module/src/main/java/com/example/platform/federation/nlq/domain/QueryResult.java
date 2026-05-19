package com.example.platform.federation.nlq.domain;

import java.util.List;
import java.util.Map;

public record QueryResult(
    String queryId,
    String previewId,
    List<String> columns,
    List<Map<String, Object>> rows,
    int rowCount,
    boolean truncated,
    long durationMs,
    String summary,
    List<ChartSuggestion> chartSuggestions,
    List<String> warnings,
    String traceId
) {}
