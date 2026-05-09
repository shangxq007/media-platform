package com.example.platform.extension.domain;

import java.util.List;

/**
 * Report on the availability and configuration of media tools in the current environment.
 *
 * <p>Generated at startup or on demand to verify that required binaries are
 * present and executable.</p>
 *
 * @param tools         list of tool availability entries
 * @param generatedAt   timestamp when the report was generated
 */
public record ToolEnvironmentReport(List<ToolAvailability> tools, java.time.Instant generatedAt) {

    /**
     * Returns {@code true} if all registered tools are available.
     */
    public boolean allAvailable() {
        return tools.stream().allMatch(ToolAvailability::available);
    }

    /**
     * Single tool availability entry.
     *
     * @param toolKey   the tool identifier
     * @param available whether the tool binary was found and is executable
     * @param path      the resolved path to the executable
     * @param version   the tool version string (if available)
     * @param message   additional status message
     */
    public record ToolAvailability(
            String toolKey,
            boolean available,
            String path,
            String version,
            String message) {
    }
}
