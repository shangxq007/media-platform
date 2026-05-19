package com.example.platform.federation.nlq.api.dto;

import java.util.List;
import java.util.Map;

public record NlqChartSuggestionsRequest(
    String userId,
    List<String> columns,
    List<Map<String, Object>> rows
) {}
