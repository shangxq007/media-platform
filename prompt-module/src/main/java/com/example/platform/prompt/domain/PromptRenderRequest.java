package com.example.platform.prompt.domain;

import java.util.Map;

/**
 * Request to render a prompt template.
 */
public record PromptRenderRequest(
        String templateId,
        String promptVersion,
        Map<String, Object> variables,
        boolean dryRun) {
}
