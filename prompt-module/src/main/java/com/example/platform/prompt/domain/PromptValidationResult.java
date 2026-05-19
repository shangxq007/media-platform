package com.example.platform.prompt.domain;

import java.util.List;

/**
 * Result of validating a prompt template.
 */
public record PromptValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings) {

    public static PromptValidationResult ok() {
        return new PromptValidationResult(true, List.of(), List.of());
    }

    public static PromptValidationResult error(List<String> errors) {
        return new PromptValidationResult(false, errors, List.of());
    }
}
