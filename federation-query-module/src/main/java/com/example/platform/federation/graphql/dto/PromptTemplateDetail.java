package com.example.platform.federation.graphql.dto;

import java.util.List;

public record PromptTemplateDetail(
        String id,
        String name,
        String status,
        String currentVersion,
        List<String> tags,
        List<PromptVersion> versions,
        List<PromptExecution> executions
) {}
