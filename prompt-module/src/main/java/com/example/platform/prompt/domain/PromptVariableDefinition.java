package com.example.platform.prompt.domain;

import java.util.List;

/**
 * Definition of a single prompt template variable.
 */
public record PromptVariableDefinition(
        String name,
        PromptVariableType type,
        boolean required,
        String defaultValue,
        String description,
        Integer minLength,
        Integer maxLength,
        List<String> allowedValues,
        boolean sensitive,
        String redactionPolicy) {

    public enum RedactionPolicy {
        FULL,
        PARTIAL,
        HASH,
        MASK_LAST_FOUR
    }
}
