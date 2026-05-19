package com.example.platform.extension.api.dto;

public record ExecuteExtensionRequest(
        String inputJson,
        String tenantId,
        String userId
) {}
