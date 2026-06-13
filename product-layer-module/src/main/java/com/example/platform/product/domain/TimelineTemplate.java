package com.example.platform.product.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Template for creating new projects with pre-configured timelines.
 * Supports versioning and effect presets.
 */
public record TimelineTemplate(
        String id,
        String workspaceId,
        String name,
        String description,
        String category,
        String creatorId,
        TemplateStatus status,
        String timelineJson,
        List<String> effectKeys,
        Map<String, Object> metadata,
        int version,
        String parentTemplateId,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Create a new template from a timeline.
     */
    public static TimelineTemplate create(String id, String workspaceId, String name,
                                           String description, String category,
                                           String creatorId, String timelineJson,
                                           List<String> effectKeys) {
        Instant now = Instant.now();
        return new TimelineTemplate(id, workspaceId, name, description, category, creatorId,
                TemplateStatus.DRAFT, timelineJson, effectKeys, Map.of(), 1, null, now, now);
    }

    /**
     * Create a new version of this template.
     */
    public TimelineTemplate createNewVersion(String newId, String timelineJson, List<String> effectKeys) {
        Instant now = Instant.now();
        return new TimelineTemplate(newId, workspaceId, name, description, category, creatorId,
                TemplateStatus.DRAFT, timelineJson, effectKeys, metadata, version + 1, id, now, now);
    }

    /**
     * Publish the template.
     */
    public TimelineTemplate publish() {
        return new TimelineTemplate(id, workspaceId, name, description, category, creatorId,
                TemplateStatus.PUBLISHED, timelineJson, effectKeys, metadata, version, parentTemplateId,
                createdAt, Instant.now());
    }

    /**
     * Archive the template.
     */
    public TimelineTemplate archive() {
        return new TimelineTemplate(id, workspaceId, name, description, category, creatorId,
                TemplateStatus.ARCHIVED, timelineJson, effectKeys, metadata, version, parentTemplateId,
                createdAt, Instant.now());
    }

    /**
     * Check if template is usable.
     */
    public boolean isUsable() {
        return status == TemplateStatus.PUBLISHED;
    }

    /**
     * Get the template chain (for version history).
     */
    public boolean hasParent() {
        return parentTemplateId != null;
    }
}
