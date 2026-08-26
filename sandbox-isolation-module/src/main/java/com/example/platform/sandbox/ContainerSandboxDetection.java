package com.example.platform.sandbox;

import java.util.Objects;
import java.util.Optional;

/** Result of a real engine probe; binary presence is kept distinct from usable enforcement. */
@org.springframework.modulith.NamedInterface("API")
public record ContainerSandboxDetection(
        boolean supportedEngineInstalled,
        Optional<ContainerSandboxProcessLauncher> launcher,
        String diagnostic) {
    public ContainerSandboxDetection {
        Objects.requireNonNull(launcher, "launcher");
        Objects.requireNonNull(diagnostic, "diagnostic");
    }
}
