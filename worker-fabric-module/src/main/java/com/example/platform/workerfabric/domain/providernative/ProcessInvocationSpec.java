package com.example.platform.workerfabric.domain.providernative;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Typed process invocation mechanics: executable identity plus ordered argv.
 *
 * <p>The authority is never a single shell-interpreted string.
 */
public record ProcessInvocationSpec(
        String executable,
        List<String> arguments,
        Map<String, String> environmentOverrides,
        Optional<Path> workingDirectory) implements InvocationSpec {

    public ProcessInvocationSpec {
        if (executable == null || executable.isBlank()) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.UNSUPPORTED_INVOCATION_FORM,
                    "process invocation requires an executable identity");
        }
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(environmentOverrides, "environmentOverrides");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        var canonicalArguments = new ArrayList<String>(arguments.size());
        for (String argument : arguments) {
            canonicalArguments.add(Objects.requireNonNull(argument, "arguments element"));
        }
        var canonicalEnvironment = new TreeMap<String, String>();
        environmentOverrides.forEach((key, value) -> canonicalEnvironment.put(
                Objects.requireNonNull(key, "environmentOverrides key"),
                Objects.requireNonNull(value, "environmentOverrides value")));
        arguments = List.copyOf(canonicalArguments);
        environmentOverrides = Map.copyOf(canonicalEnvironment);
    }

    public static ProcessInvocationSpec of(String executable, List<String> arguments) {
        return new ProcessInvocationSpec(executable, arguments, Map.of(), Optional.empty());
    }

    @Override
    public InvocationKind kind() {
        return InvocationKind.PROCESS;
    }
}
