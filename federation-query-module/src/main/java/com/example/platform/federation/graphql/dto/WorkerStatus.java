package com.example.platform.federation.graphql.dto;

import java.util.List;

public record WorkerStatus(
        String id,
        String status,
        boolean gpuAvailable,
        List<String> providerKeys
) {}
