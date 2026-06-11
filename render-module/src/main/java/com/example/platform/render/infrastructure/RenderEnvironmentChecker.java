package com.example.platform.render.infrastructure;

import java.nio.file.Path;
import java.util.List;

public interface RenderEnvironmentChecker {

    RenderEnvironmentCheckResult check(ExecutionMode mode, Path workingDir, Path outputDir);

    List<String> requiredBinaries(ExecutionMode mode);
}
