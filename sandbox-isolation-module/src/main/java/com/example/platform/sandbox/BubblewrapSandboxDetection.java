package com.example.platform.sandbox;

import java.util.Objects;
import java.util.Optional;

/** Result of a real production-shape bwrap probe, distinct from binary presence. */
@org.springframework.modulith.NamedInterface("API")
public record BubblewrapSandboxDetection(
        boolean bubblewrapInstalled,
        Optional<BubblewrapSandboxProcessLauncher> launcher,
        String diagnostic) {
    public BubblewrapSandboxDetection {
        Objects.requireNonNull(launcher, "launcher");
        Objects.requireNonNull(diagnostic, "diagnostic");
    }
}
