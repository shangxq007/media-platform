package com.example.platform.shared.usage;

/** Neutral runtime-facing append port for observed usage. */
@FunctionalInterface
public interface ObservedRuntimeUsageEmissionPort {
    ObservedRuntimeUsage emit(ObservedRuntimeUsage observation);
}
