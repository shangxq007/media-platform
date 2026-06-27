package com.example.platform.render.infrastructure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Inventory of locally available render tools and their versions.
 *
 * <p>Read-only detection — does NOT change dispatch behavior, does NOT
 * install tools, does NOT mark providers as production-ready.</p>
 *
 * <p>Reports tool availability for future provider planning.
 * Missing tools are reported as unavailable, not failure.</p>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>No production dispatch changes</li>
 *   <li>No tool installation</li>
 *   <li>No untrusted code execution</li>
 *   <li>Safe version commands only</li>
 *   <li>No local paths exposed in public API</li>
 * </ul>
 */
@Component
public class RenderToolCapabilityInventory {

    private static final Logger log = LoggerFactory.getLogger(RenderToolCapabilityInventory.class);

    /**
     * Detect available render tools and their versions.
     *
     * @return list of tool inventory entries
     */
    public List<ToolInventoryEntry> detectTools() {
        List<ToolInventoryEntry> entries = new ArrayList<>();

        entries.add(detectTool("ffmpeg", "ffmpeg", "-version"));
        entries.add(detectTool("ffprobe", "ffprobe", "-version"));
        entries.add(detectTool("melt", "melt", "--version"));
        entries.add(detectTool("blender", "blender", "--version"));
        entries.add(detectTool("natron", "natron", "--version"));
        entries.add(detectTool("gst-launch-1.0", "gst-launch-1.0", "--version"));
        entries.add(detectTool("MP4Box", "MP4Box", "-version"));
        entries.add(detectTool("node", "node", "--version"));
        entries.add(detectTool("npm", "npm", "--version"));
        entries.add(detectTool("python3", "python3", "--version"));

        return entries;
    }

    /**
     * Get a summary map of tool name → availability status.
     */
    public Map<String, Boolean> getAvailabilitySummary() {
        Map<String, Boolean> summary = new LinkedHashMap<>();
        for (ToolInventoryEntry entry : detectTools()) {
            summary.put(entry.name(), entry.available());
        }
        return summary;
    }

    /**
     * Check if a specific tool is available.
     */
    public boolean isToolAvailable(String toolName) {
        return detectTools().stream()
                .anyMatch(e -> e.name().equals(toolName) && e.available());
    }

    private ToolInventoryEntry detectTool(String name, String binary, String versionFlag) {
        try {
            ProcessBuilder pb = new ProcessBuilder(binary, versionFlag);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            int exitCode = done ? p.exitValue() : -1;

            if (exitCode == 0 || (output != null && !output.isBlank())) {
                String version = extractVersion(output);
                log.debug("Tool detected: {} version={}", name, version);
                return new ToolInventoryEntry(name, binary, true, version, null);
            }
        } catch (IOException e) {
            // Tool not found — expected, not a failure
            log.trace("Tool not found: {}", name);
        } catch (Exception e) {
            log.trace("Tool detection error for {}: {}", name, e.getMessage());
        }

        return new ToolInventoryEntry(name, binary, false, null, null);
    }

    private String extractVersion(String output) {
        if (output == null || output.isBlank()) return "unknown";
        // Take first line, truncate to 120 chars
        String firstLine = output.lines().findFirst().orElse("unknown");
        return firstLine.length() > 120 ? firstLine.substring(0, 120) + "..." : firstLine;
    }

    /**
     * Inventory entry for a render tool.
     *
     * @param name      tool display name
     * @param binary    binary name
     * @param available whether the tool is available
     * @param version   version string (null if unavailable)
     * @param notes     additional notes (null if none)
     */
    public record ToolInventoryEntry(
            String name,
            String binary,
            boolean available,
            String version,
            String notes) {}
}
