package com.example.platform.render.domain.environment;

import java.util.List;
import java.util.Map;

/**
 * Platform model for an OpenCue job submission.
 * No OpenCue client objects — pure platform model.
 */
public record OpenCueJobSpec(
        String jobName,
        String owner,
        int priority,
        List<String> tags,
        Map<String, String> environmentVariables,
        Map<String, Object> resourceRequirements,
        List<OpenCueLayerSpec> layers) {

    public record OpenCueLayerSpec(
            String layerName,
            List<String> commands,
            int frameCount,
            Map<String, String> frameEnvironment) {}

    public static OpenCueJobSpec fromJobId(String jobId) {
        return new OpenCueJobSpec("platform-" + jobId, "platform", 50,
                List.of("platform"), Map.of(),
                Map.of("cpu", 1, "memoryMb", 1024), List.of());
    }
}
