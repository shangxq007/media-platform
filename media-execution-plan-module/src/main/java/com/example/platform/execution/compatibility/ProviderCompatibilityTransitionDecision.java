package com.example.platform.execution.compatibility;

/** Typed, fail-closed algebra for one dependency and exact producer/consumer candidate pair. */
public enum ProviderCompatibilityTransitionDecision {
    DIRECT_COMPATIBLE,
    ARTIFACT_MATERIALIZATION_REQUIRED,
    INCOMPATIBLE,
    UNKNOWN_FAIL_CLOSED
}
