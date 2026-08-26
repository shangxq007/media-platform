package com.example.platform.sandbox;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Probe-created mechanics configuration; it carries no semantic or authorization authority. */
record ContainerEngineConfiguration(
        Kind kind, Path executable, String image, Map<String, String> engineEnvironment) {
    enum Kind { PODMAN, DOCKER }

    ContainerEngineConfiguration {
        executable = executable.toAbsolutePath().normalize();
        engineEnvironment = Map.copyOf(engineEnvironment);
    }

    List<String> immediateRemovalCommand(String name) {
        return switch (kind) {
            case PODMAN -> List.of(
                    executable.toString(), "rm", "--force", "--time=0", name);
            case DOCKER -> List.of(executable.toString(), "rm", "--force", name);
        };
    }
}
