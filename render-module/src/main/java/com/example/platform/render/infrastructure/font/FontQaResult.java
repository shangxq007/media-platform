package com.example.platform.render.infrastructure.font;

import java.util.List;

public record FontQaResult(
        boolean passed,
        FontQaProfile profile,
        FontQaSeverity severity,
        List<FontQaCheck> checks,
        List<String> warnings,
        List<String> errors,
        String reportArtifact
) {
    public static FontQaResult passed(FontQaProfile profile, List<FontQaCheck> checks) {
        return new FontQaResult(true, profile, FontQaSeverity.INFO, checks,
                List.of(), List.of(), null);
    }

    public static FontQaResult failed(FontQaProfile profile, List<FontQaCheck> checks,
                                       List<String> errors) {
        return new FontQaResult(false, profile, FontQaSeverity.ERROR, checks,
                List.of(), errors, null);
    }
}
