package com.example.platform.prompt.domain;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Prompt template with full metadata.
 */
public record PromptTemplate(
        String templateId,
        String name,
        String description,
        String category,
        List<String> tags,
        String owner,
        PromptTemplateStatus status,
        String schemaVersion,
        String currentPromptVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public PromptTemplate withStatus(PromptTemplateStatus newStatus) {
        return new PromptTemplate(templateId, name, description, category, tags, owner,
                newStatus, schemaVersion, currentPromptVersion, createdAt, OffsetDateTime.now());
    }

    public PromptTemplate withCurrentVersion(String version) {
        return new PromptTemplate(templateId, name, description, category, tags, owner,
                status, schemaVersion, version, createdAt, OffsetDateTime.now());
    }

    public PromptTemplate withUpdatedFields(String name, String description, String category, List<String> tags) {
        return new PromptTemplate(templateId, name, description, category, tags, owner,
                status, schemaVersion, currentPromptVersion, createdAt, OffsetDateTime.now());
    }
}
