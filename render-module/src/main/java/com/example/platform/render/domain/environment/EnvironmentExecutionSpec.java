package com.example.platform.render.domain.environment;

import com.example.platform.render.domain.execution.BackendExecutionSpec;
import java.util.List;
import java.util.Map;

/**
 * Environment-specific execution spec. Contains backend spec + environment metadata.
 */
public record EnvironmentExecutionSpec(
        String environmentId,
        String environmentType,
        BackendExecutionSpec backendSpec,
        List<String> backendCapabilities,
        Map<String, Object> resourceRequirements,
        Map<String, String> schedulingHints) {

    public static EnvironmentExecutionSpec of(String envId, String envType,
                                                BackendExecutionSpec backendSpec) {
        return new EnvironmentExecutionSpec(envId, envType, backendSpec,
                List.of(), Map.of(), Map.of());
    }
}
