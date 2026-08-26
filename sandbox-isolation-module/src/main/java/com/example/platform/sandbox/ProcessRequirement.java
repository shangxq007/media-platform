package com.example.platform.sandbox;

import java.time.Duration;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exact executable/argv plus mandatory process-tree, timeout and cancellation behavior. */
@org.springframework.modulith.NamedInterface("API")
public record ProcessRequirement(
        Set<String> allowedExecutables,
        String executable,
        List<String> arguments,
        Duration timeout,
        boolean containProcessTree,
        boolean cancellationRequired) {

    private static final Set<String> SHELLS = Set.of(
            "sh", "bash", "dash", "zsh", "/bin/sh", "/bin/bash", "/usr/bin/sh", "/usr/bin/bash");

    public ProcessRequirement {
        allowedExecutables = Set.copyOf(Objects.requireNonNull(allowedExecutables, "allowedExecutables"));
        Objects.requireNonNull(executable, "executable");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(timeout, "timeout");
        if (executable.isBlank() || !Path.of(executable).isAbsolute()
                || !Path.of(executable).normalize().toString().equals(executable)
                || !allowedExecutables.contains(executable)) {
            throw new IllegalArgumentException("executable is not explicitly allowed");
        }
        if (SHELLS.contains(executable) || SHELLS.contains(Path.of(executable).getFileName().toString())) {
            throw new IllegalArgumentException("shell executables are forbidden");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        for (String argument : arguments) {
            if (argument == null || argument.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("argv contains an invalid value");
            }
        }
        if (!containProcessTree || !cancellationRequired) {
            throw new IllegalArgumentException("containment and cancellation are mandatory");
        }
    }

    public static ProcessRequirement of(
            Set<String> allowlist, String executable, List<String> arguments, Duration timeout) {
        return new ProcessRequirement(allowlist, executable, arguments, timeout, true, true);
    }

    public List<String> command() {
        var command = new java.util.ArrayList<String>(arguments.size() + 1);
        command.add(executable);
        command.addAll(arguments);
        return List.copyOf(command);
    }
}
