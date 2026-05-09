package com.example.platform.ai.app;

import java.time.Instant;

public record AiScriptResult(
        String scriptContent,
        String modelUsed,
        int tokensUsed,
        Instant generatedAt) {}
