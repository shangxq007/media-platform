package com.example.platform.federation.nlq.domain;

import java.util.Map;

public record ReportWidget(
    String widgetId,
    String title,
    String queryId,
    String savedQuery,
    String chartType,
    String layout,
    Map<String, Object> config
) {}
