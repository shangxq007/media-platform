package com.example.platform.federation.nlq.api.dto;

import com.example.platform.federation.nlq.domain.ReportSchedule;
import com.example.platform.federation.nlq.domain.ReportWidget;

import java.util.List;

public record ReportUpdateRequest(
    String name,
    String description,
    List<ReportWidget> widgets,
    List<String> queryDefinitions,
    String visibility,
    ReportSchedule schedule
) {}
