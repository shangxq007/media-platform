package com.example.platform.render.infrastructure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RemotionEnvironmentCheck implements RenderEnvironmentChecker {

    @Override
    public RenderEnvironmentCheckResult check(ExecutionMode mode, Path workingDir, Path outputDir) {
        List<RenderEnvironmentCheckResult.EnvironmentCheckEntry> entries = new ArrayList<>();

        if (mode == ExecutionMode.MOCK) {
            entries.add(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                    "remotion", true, "MOCK mode: skipped"));
            return RenderEnvironmentCheckResult.ok(entries);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("npx", "remotion", "--version");
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit == 0) {
                entries.add(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                        "remotion", true, "remotion CLI found"));
            } else {
                entries.add(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                        "remotion", false, "remotion CLI returned non-zero exit"));
            }
        } catch (Exception e) {
            entries.add(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                    "remotion", false, "remotion CLI not found: " + e.getMessage()));
        }

        boolean allPassed = entries.stream().allMatch(RenderEnvironmentCheckResult.EnvironmentCheckEntry::passed);
        return new RenderEnvironmentCheckResult(allPassed, entries);
    }

    @Override
    public List<String> requiredBinaries(ExecutionMode mode) {
        if (mode == ExecutionMode.MOCK) return List.of();
        return List.of("npx", "remotion");
    }
}
