package com.example.platform.render.infrastructure.font;

import java.util.List;

public record FontCiAcceptanceResult(
        boolean passed,
        FontCiAcceptancePolicy policy,
        List<FontQaCheck> checks,
        List<String> warnings,
        List<String> errors,
        String reportArtifactPath
) {}
