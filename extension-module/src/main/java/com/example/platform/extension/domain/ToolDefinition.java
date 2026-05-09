package com.example.platform.extension.domain;

import java.util.List;

/**
 * Declarative definition of a media tool that can be executed by the platform.
 *
 * <p>Each tool has a unique key, an absolute path to its executable, a set of
 * capabilities it supports, and an optional sandbox policy. Tool definitions are
 * loaded from configuration and registered in the {@link ToolRegistry}.</p>
 *
 * @param key          unique tool identifier (e.g., "ffmpeg", "melt", "mp4box")
 * @param label        human-readable display name
 * @param description  description of the tool's purpose
 * @param executable   absolute path to the executable (must be in the allowlist)
 * @param capabilities list of capabilities this tool supports
 * @param sandboxPolicy sandbox policy governing execution constraints
 */
public record ToolDefinition(
        String key,
        String label,
        String description,
        String executable,
        List<ToolCapability> capabilities,
        ToolSandboxPolicy sandboxPolicy) {
}
