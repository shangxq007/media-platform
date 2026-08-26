package com.example.platform.sandbox;

import java.util.Objects;
import java.util.Set;

@org.springframework.modulith.NamedInterface("API")
public record SandboxFailure(
        SandboxFailureCode code, String message, Set<SandboxCapability> missingCapabilities) {
    public SandboxFailure {
        Objects.requireNonNull(code, "code"); Objects.requireNonNull(message, "message");
        missingCapabilities = Set.copyOf(missingCapabilities);
    }
    public static SandboxFailure of(SandboxFailureCode code, String message, Set<SandboxCapability> missing) {
        return new SandboxFailure(code, message, missing);
    }
}
