package com.example.platform.render.infrastructure;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class DefaultRenderEnvironmentChecker implements RenderEnvironmentChecker {

    private final List<RenderEnvironmentChecker> delegates;

    public DefaultRenderEnvironmentChecker(List<RenderEnvironmentChecker> delegates) {
        this.delegates = delegates;
    }

    @Override
    public RenderEnvironmentCheckResult check(ExecutionMode mode, Path workingDir, Path outputDir) {
        if (mode == ExecutionMode.MOCK) {
            return RenderEnvironmentCheckResult.ok(
                    List.of(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                            "all", true, "MOCK mode: all environment checks skipped")));
        }

        List<RenderEnvironmentCheckResult.EnvironmentCheckEntry> entries = new java.util.ArrayList<>();
        boolean allPassed = true;

        for (RenderEnvironmentChecker delegate : delegates) {
            RenderEnvironmentCheckResult result = delegate.check(mode, workingDir, outputDir);
            entries.addAll(result.checks());
            if (!result.allPassed()) {
                allPassed = false;
            }
        }

        if (workingDir != null && !Files.isDirectory(workingDir)) {
            entries.add(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                    "workingDir", false, "Working directory does not exist: " + workingDir));
            allPassed = false;
        } else if (workingDir != null && !Files.isWritable(workingDir)) {
            entries.add(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                    "workingDir", false, "Working directory is not writable: " + workingDir));
            allPassed = false;
        }

        if (outputDir != null && !Files.isDirectory(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (Exception e) {
                entries.add(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                        "outputDir", false, "Cannot create output directory: " + outputDir));
                allPassed = false;
            }
        } else if (outputDir != null && !Files.isWritable(outputDir)) {
            entries.add(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                    "outputDir", false, "Output directory is not writable: " + outputDir));
            allPassed = false;
        }

        return new RenderEnvironmentCheckResult(allPassed, entries);
    }

    @Override
    public List<String> requiredBinaries(ExecutionMode mode) {
        if (mode == ExecutionMode.MOCK) return List.of();
        return delegates.stream()
                .flatMap(d -> d.requiredBinaries(mode).stream())
                .distinct()
                .toList();
    }
}
