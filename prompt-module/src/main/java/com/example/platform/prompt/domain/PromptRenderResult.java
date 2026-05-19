package com.example.platform.prompt.domain;

import java.util.List;

/**
 * Result of rendering a prompt template.
 */
public record PromptRenderResult(
        String renderedPrompt,
        String redactedPrompt,
        List<String> missingVariables,
        List<String> warnings) {
}
