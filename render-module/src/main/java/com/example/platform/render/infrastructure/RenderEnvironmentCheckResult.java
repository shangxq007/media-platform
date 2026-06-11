package com.example.platform.render.infrastructure;

import java.nio.file.Path;
import java.util.List;

public record RenderEnvironmentCheckResult(
        boolean allPassed,
        List<EnvironmentCheckEntry> checks
) {
    public record EnvironmentCheckEntry(
            String name,
            boolean passed,
            String message
    ) {}

    public static RenderEnvironmentCheckResult ok(List<EnvironmentCheckEntry> checks) {
        return new RenderEnvironmentCheckResult(true, checks);
    }

    public static RenderEnvironmentCheckResult failed(List<EnvironmentCheckEntry> checks) {
        return new RenderEnvironmentCheckResult(false, checks);
    }
}
