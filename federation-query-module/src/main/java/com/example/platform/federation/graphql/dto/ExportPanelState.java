package com.example.platform.federation.graphql.dto;

import java.util.List;

public record ExportPanelState(
        ProjectInfo project,
        TimelineSummary timelineSummary,
        List<ExportOption> exportOptions,
        List<WorkerStatus> workers,
        ExportValidation validation
) {}
