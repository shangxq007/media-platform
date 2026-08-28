package com.example.platform.providerplugin;

import com.example.platform.sandbox.SandboxCancellation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Bounded host-owned inputs used to create one provider-native runtime binding. */
public record ProviderPluginRuntimeContext(
        Path executable,
        Path workspaceRoot,
        Duration timeout,
        long captureBytes,
        SandboxCancellation cancellation) {

    public ProviderPluginRuntimeContext {
        executable = Objects.requireNonNull(executable, "executable").toAbsolutePath().normalize();
        workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot")
                .toAbsolutePath().normalize();
        timeout = Objects.requireNonNull(timeout, "timeout");
        cancellation = Objects.requireNonNull(cancellation, "cancellation");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (captureBytes <= 0) {
            throw new IllegalArgumentException("captureBytes must be positive");
        }
    }
}
