package com.example.platform.extension.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request to execute a media tool.
 *
 * <p>The executable must be a whitelisted path from the {@link ToolRegistry}.
 * Arguments are passed as a {@link List<String>} — never concatenated into a
 * shell string.</p>
 *
 * @param toolKey        the tool identifier (must match a registered {@link ToolDefinition#getKey()})
 * @param args           command-line arguments (each element is a separate arg)
 * @param environment    exact environment allowlist; ambient inheritance is forbidden
 * @param workingDirectory explicit working directory, or null only when policy supplies one
 * @param timeoutMillis  override timeout (0 = use the bounded policy value)
 */
public record ToolExecutionRequest(
        String toolKey,
        List<String> args,
        Map<String, String> environment,
        String workingDirectory,
        long timeoutMillis) {

    public ToolExecutionRequest {
        if (toolKey == null || toolKey.isBlank()) throw new IllegalArgumentException("toolKey is required");
        args = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(args, "args")));
        environment = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(environment, "environment")));
        if (timeoutMillis < 0) throw new IllegalArgumentException("timeoutMillis must not be negative");
    }

    /**
     * Creates a minimal execution request with just the tool key and args.
     */
    public static ToolExecutionRequest of(String toolKey, List<String> args) {
        return new ToolExecutionRequest(toolKey, args, Map.of(), null, 0);
    }

    /**
     * Creates an execution request with tool key, args, and timeout.
     */
    public static ToolExecutionRequest withTimeout(String toolKey, List<String> args, long timeoutMillis) {
        return new ToolExecutionRequest(toolKey, args, Map.of(), null, timeoutMillis);
    }

    /**
     * Copies this exact request with a different timeout.
     */
    public ToolExecutionRequest withTimeout(long timeoutMillis) {
        return new ToolExecutionRequest(
                toolKey, args, environment, workingDirectory, timeoutMillis);
    }
}
