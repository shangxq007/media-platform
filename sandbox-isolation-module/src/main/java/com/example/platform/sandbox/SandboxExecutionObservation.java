package com.example.platform.sandbox;

import java.nio.file.Path;
import java.time.Duration;

/** Ephemeral mutable-runtime observation, excluded from canonical task semantics. */
@org.springframework.modulith.NamedInterface("API")
public record SandboxExecutionObservation(
        SandboxExecutionHandle handle,
        Path workingDirectory,
        Duration elapsed,
        SandboxCleanupObservation cleanup) {}
