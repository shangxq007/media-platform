package com.example.platform.render.infrastructure;

import java.nio.file.Path;
import java.util.List;

public class NodeEnvironmentCheck implements RenderEnvironmentChecker {

    @Override
    public RenderEnvironmentCheckResult check(ExecutionMode mode, Path workingDir, Path outputDir) {
        if (mode == ExecutionMode.MOCK) {
            return RenderEnvironmentCheckResult.ok(
                    List.of(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                            "node", true, "MOCK mode: skipped")));
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit == 0) {
                return RenderEnvironmentCheckResult.ok(
                        List.of(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                                "node", true, "node found")));
            }
        } catch (Exception e) {
            // fall through
        }
        return RenderEnvironmentCheckResult.failed(
                List.of(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                        "node", false, "node not found or not executable")));
    }

    @Override
    public List<String> requiredBinaries(ExecutionMode mode) {
        if (mode == ExecutionMode.MOCK) return List.of();
        return List.of("node");
    }
}
