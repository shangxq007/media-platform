package com.example.platform.extension.app;

import com.example.platform.extension.domain.ToolCapability;
import com.example.platform.extension.domain.ToolDefinition;
import com.example.platform.extension.domain.ToolEnvironmentReport;
import com.example.platform.extension.domain.ToolEnvironmentReport.ToolAvailability;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry of all media tools available to the platform.
 *
 * <p>Tool definitions are loaded from configuration ({@code app.cli-tools.executables}
 * and {@code app.cli-tools.tools}) and registered at startup. The registry
 * enforces that only whitelisted executables can be executed.</p>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>Only executables whose absolute path is in the configured allowlist are permitted.</li>
 *   <li>Path traversal attempts are rejected.</li>
 *   <li>All lookups are case-sensitive.</li>
 * </ul>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, String> allowedExecutables = new ConcurrentHashMap<>();

    /**
     * Registers an allowed executable path.
     *
     * @param key  logical key (e.g., "ffmpeg")
     * @param path absolute path to the executable (e.g., "/usr/bin/ffmpeg")
     * @throws IllegalArgumentException if the path is not absolute or contains traversal
     */
    public void registerExecutable(String key, String path) {
        validateAbsolutePath(path);
        allowedExecutables.put(key, path);
        log.info("Registered executable: {} -> {}", key, path);
    }

    /**
     * Registers a tool definition.
     *
     * @param definition the tool definition
     * @throws IllegalArgumentException if the executable is not in the allowlist
     */
    public void registerTool(ToolDefinition definition) {
        if (!allowedExecutables.containsValue(definition.executable())) {
            throw new IllegalArgumentException(
                    "Executable not in allowlist: " + definition.executable()
                            + " for tool: " + definition.key());
        }
        tools.put(definition.key(), definition);
        log.info("Registered tool: {} ({})", definition.key(), definition.label());
    }

    /**
     * Looks up a tool definition by key.
     *
     * @param key the tool identifier
     * @return optional containing the tool definition
     */
    public Optional<ToolDefinition> findTool(String key) {
        return Optional.ofNullable(tools.get(key));
    }

    /**
     * Returns all registered tool definitions.
     */
    public List<ToolDefinition> listTools() {
        return List.copyOf(tools.values());
    }

    /**
     * Resolves the executable path for a given tool key.
     *
     * @param toolKey the tool identifier
     * @return the absolute path to the executable
     * @throws IllegalArgumentException if the tool is not registered
     */
    public String resolveExecutable(String toolKey) {
        ToolDefinition definition = tools.get(toolKey);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolKey);
        }
        return definition.executable();
    }

    /**
     * Checks whether the given absolute path is in the executable allowlist.
     *
     * @param path the absolute path to check
     * @return {@code true} if the path is allowed
     */
    public boolean isAllowedExecutable(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        validateAbsolutePath(path);
        return allowedExecutables.containsValue(path);
    }

    /**
     * Generates an environment report indicating which tools are available.
     *
     * @return the environment report
     */
    public ToolEnvironmentReport validateEnvironment() {
        List<ToolAvailability> availabilities = new ArrayList<>();
        for (ToolDefinition tool : tools.values()) {
            String path = tool.executable();
            File file = new File(path);
            boolean available = file.exists() && file.canExecute();
            String message = available ? "Available" : "Not found or not executable: " + path;
            availabilities.add(new ToolAvailability(
                    tool.key(), available, path, null, message));
        }
        return new ToolEnvironmentReport(availabilities, Instant.now());
    }

    private void validateAbsolutePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Executable path must not be null or blank");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Executable path must be absolute (got: " + path + ")");
        }
        // Reject null bytes
        if (path.contains("\0")) {
            throw new IllegalArgumentException("Executable path contains null byte");
        }
        // Reject backslashes
        if (path.contains("\\")) {
            throw new IllegalArgumentException("Executable path contains backslash");
        }
        // Reject percent-encoded traversal
        String lower = path.toLowerCase();
        if (lower.contains("%2e%2e") || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("%252e") || lower.contains("%252f") || lower.contains("%255c")) {
            throw new IllegalArgumentException(
                    "Path traversal detected in executable path: " + path);
        }
        // Segment-level validation
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException(
                        "Path traversal detected in executable path: " + path);
            }
        }
    }
}
