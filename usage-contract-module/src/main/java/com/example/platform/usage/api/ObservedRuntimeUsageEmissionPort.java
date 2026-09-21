package com.example.platform.usage.api;

/** Neutral runtime-facing append port for observed usage. */
@FunctionalInterface
public interface ObservedRuntimeUsageEmissionPort {
    ObservedRuntimeUsage emit(ObservedRuntimeUsage observation);
}
