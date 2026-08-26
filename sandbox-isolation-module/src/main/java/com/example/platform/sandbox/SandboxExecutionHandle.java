package com.example.platform.sandbox;

import java.time.Instant;

/** Ephemeral process evidence; never part of immutable task/ETG semantics. */
@org.springframework.modulith.NamedInterface("API")
public record SandboxExecutionHandle(long processId, Instant launchedAt) {}
