package com.example.platform.product.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Render preset for standardized output configurations.
 * Enables reuse of render settings across projects.
 */
public record RenderPreset(
        String id,
        String workspaceId,
        String name,
        String description,
        String creatorId,
        String format,
        String resolution,
        String profile,
        Map<String, Object> settings,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Create a new render preset.
     */
    public static RenderPreset create(String id, String workspaceId, String name,
                                       String description, String creatorId,
                                       String format, String resolution, String profile) {
        Instant now = Instant.now();
        return new RenderPreset(id, workspaceId, name, description, creatorId,
                format, resolution, profile, Map.of(), false, now, now);
    }

    /**
     * Create a default preset.
     */
    public static RenderPreset createDefault(String id, String workspaceId, String creatorId) {
        Instant now = Instant.now();
        return new RenderPreset(id, workspaceId, "Default", "Default render settings", creatorId,
                "mp4", "1920x1080", "standard", Map.of(), true, now, now);
    }

    /**
     * Update preset details.
     */
    public RenderPreset withDetails(String name, String description) {
        return new RenderPreset(id, workspaceId, name, description, creatorId,
                format, resolution, profile, settings, isDefault, createdAt, Instant.now());
    }

    /**
     * Update render settings.
     */
    public RenderPreset withSettings(String format, String resolution, String profile,
                                      Map<String, Object> settings) {
        return new RenderPreset(id, workspaceId, name, description, creatorId,
                format, resolution, profile, settings, isDefault, createdAt, Instant.now());
    }

    /**
     * Get a setting value.
     */
    public Object getSetting(String key) {
        return settings.get(key);
    }

    /**
     * Check if preset has a specific setting.
     */
    public boolean hasSetting(String key) {
        return settings.containsKey(key);
    }
}
