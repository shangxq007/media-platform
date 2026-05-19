package com.example.platform.prompt.domain;

import java.util.List;
import java.util.Map;

/**
 * Schema definition for prompt template variables.
 */
public record PromptVariableSchema(
        String schemaId,
        String templateId,
        String promptVersion,
        List<PromptVariableDefinition> variables) {

    public List<String> requiredVariableNames() {
        return variables.stream()
                .filter(PromptVariableDefinition::required)
                .map(PromptVariableDefinition::name)
                .toList();
    }

    public List<String> sensitiveVariableNames() {
        return variables.stream()
                .filter(PromptVariableDefinition::sensitive)
                .map(PromptVariableDefinition::name)
                .toList();
    }

    public Map<String, PromptVariableDefinition> variableMap() {
        return variables.stream().collect(java.util.stream.Collectors.toMap(
                PromptVariableDefinition::name, v -> v));
    }
}
