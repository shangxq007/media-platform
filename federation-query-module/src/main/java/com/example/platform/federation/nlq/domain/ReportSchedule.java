package com.example.platform.federation.nlq.domain;

import java.util.List;

public record ReportSchedule(
    String cronExpression,
    String timezone,
    List<String> recipients,
    String format,
    boolean enabled
) {}
