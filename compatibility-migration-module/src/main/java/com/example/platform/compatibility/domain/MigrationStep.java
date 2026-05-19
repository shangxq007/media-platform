package com.example.platform.compatibility.domain;

import java.util.Map;

public record MigrationStep(
        String stepId,
        String description,
        String adapterKey,
        Map<String, Object> config
) {}
