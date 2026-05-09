package com.example.platform.extension.domain;

import java.util.List;
import java.util.Map;

/**
 * Request to execute a media tool.
 *
 * <p>The executable must be a whitelisted path from the {@link ToolRegistry}.
 * Arguments are passed as a {@link List<String>} — never concatenated into a
 * shell string.</p>
 *
 * @param toolKey        the tool identifier (must match a registered {@link ToolDefinition#getKey()})
 * @param args           command-line arguments (each element is a separate arg)
 * @param environment    additional environment variables (null = inherit)
 * @param workingDirectory override working directory (null = use sandbox policy default)
 * @param timeoutMillis  override timeout (0 = use sandbox policy default)
 */
public record ToolExecutionRequest(
        String toolKey,
        List<String> args,
        Map<String, String> environment,
        String workingDirectory,
        long timeoutMillis) {

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
}
