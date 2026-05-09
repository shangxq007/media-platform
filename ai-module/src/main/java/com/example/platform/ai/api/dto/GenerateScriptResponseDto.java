package com.example.platform.ai.api.dto;

import java.time.Instant;

public record GenerateScriptResponseDto(
        String scriptContent,
        String modelUsed,
        int tokensUsed,
        Instant generatedAt) {}
