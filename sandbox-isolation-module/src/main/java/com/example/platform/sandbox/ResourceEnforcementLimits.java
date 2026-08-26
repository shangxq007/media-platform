package com.example.platform.sandbox;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Enforcement projection only; it grants no capacity or reservation authority. */
@org.springframework.modulith.NamedInterface("API")
public record ResourceEnforcementLimits(
        Optional<Double> cpuCount,
        OptionalLong memoryBytes,
        OptionalInt processCount,
        OptionalInt openFileCount,
        OptionalLong temporaryBytes,
        OptionalLong outputBytes,
        long captureBytes) {
    public ResourceEnforcementLimits {
        if (cpuCount == null || memoryBytes == null || processCount == null || openFileCount == null
                || temporaryBytes == null || outputBytes == null || captureBytes <= 0)
            throw new IllegalArgumentException("resource enforcement projection is invalid");
        cpuCount.ifPresent(value -> { if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException("cpu count limit must be positive and finite"); });
        if (memoryBytes.stream().anyMatch(value -> value <= 0)
                || processCount.stream().anyMatch(value -> value <= 0)
                || openFileCount.stream().anyMatch(value -> value <= 0)
                || temporaryBytes.stream().anyMatch(value -> value <= 0)
                || outputBytes.stream().anyMatch(value -> value <= 0))
            throw new IllegalArgumentException("resource limits must be positive");
    }
    public static ResourceEnforcementLimits boundedDefaults() {
        return new ResourceEnforcementLimits(Optional.of(1.0),
                OptionalLong.of(512L << 20), OptionalInt.of(64), OptionalInt.of(256),
                OptionalLong.of(512L << 20), OptionalLong.of(512L << 20), 1L << 20);
    }
    public static ResourceEnforcementLimits captureOnly(long captureBytes) {
        return new ResourceEnforcementLimits(Optional.empty(), OptionalLong.empty(),
                OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty(),
                OptionalLong.empty(), captureBytes);
    }
    public boolean requiresExtendedLimits() {
        return cpuCount.isPresent() || memoryBytes.isPresent() || processCount.isPresent()
                || openFileCount.isPresent() || temporaryBytes.isPresent() || outputBytes.isPresent();
    }
}
