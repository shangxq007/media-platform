package com.example.platform.prompt.domain;

import java.time.OffsetDateTime;

/**
 * Versioned instance of a prompt template.
 */
public record PromptTemplateVersion(
        String versionId,
        String templateId,
        String promptVersion,
        String templateBody,
        String variableSchemaJson,
        String changelog,
        String createdBy,
        OffsetDateTime createdAt,
        String checksum,
        String previousVersion,
        boolean deprecated) {

    public PromptTemplateVersion markDeprecated() {
        return new PromptTemplateVersion(versionId, templateId, promptVersion, templateBody,
                variableSchemaJson, changelog, createdBy, createdAt, checksum, previousVersion, true);
    }
}
