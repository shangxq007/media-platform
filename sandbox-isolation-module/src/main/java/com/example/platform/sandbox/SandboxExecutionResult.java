package com.example.platform.sandbox;

import java.util.Optional;
import java.util.OptionalInt;

/** Ephemeral process result. Exit zero is process evidence only, never Artifact/completion evidence. */
@org.springframework.modulith.NamedInterface("API")
public record SandboxExecutionResult(
        OptionalInt exitCode,
        BoundedCapture stdout,
        BoundedCapture stderr,
        Optional<SandboxFailure> failure,
        SandboxExecutionObservation observation) {}
