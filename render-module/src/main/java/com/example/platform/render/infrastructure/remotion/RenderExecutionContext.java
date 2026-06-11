package com.example.platform.render.infrastructure.remotion;

import java.util.Map;

public record RenderExecutionContext(
        String jobId,
        String mode,
        String workingDir,
        Map<String, String> environment,
        Map<String, Object> metadata
) {}
