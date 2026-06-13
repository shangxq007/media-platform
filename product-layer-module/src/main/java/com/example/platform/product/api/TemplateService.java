package com.example.platform.product.api;

import com.example.platform.product.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for template management.
 */
@Service
@Transactional
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    // In-memory storage (replace with repository in production)
    private final Map<String, TimelineTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, RenderPreset> presets = new ConcurrentHashMap<>();

    /**
     * Create a new timeline template.
     */
    public TimelineTemplate createTemplate(String workspaceId, String name, String description,
                                            String category, String creatorId, String timelineJson,
                                            List<String> effectKeys) {
        String id = Ids.newId("tmpl");
        TimelineTemplate template = TimelineTemplate.create(id, workspaceId, name, description,
                category, creatorId, timelineJson, effectKeys);
        templates.put(id, template);

        log.info("Created template {} in workspace {}", id, workspaceId);
        return template;
    }

    /**
     * Get template by ID.
     */
    public TimelineTemplate getTemplate(String templateId) {
        TimelineTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        return template;
    }

    /**
     * List templates for a workspace.
     */
    public List<TimelineTemplate> listTemplates(String workspaceId) {
        return templates.values().stream()
                .filter(t -> workspaceId.equals(t.workspaceId()))
                .toList();
    }

    /**
     * List published templates.
     */
    public List<TimelineTemplate> listPublishedTemplates(String workspaceId) {
        return templates.values().stream()
                .filter(t -> workspaceId.equals(t.workspaceId()) && t.isUsable())
                .toList();
    }

    /**
     * Publish a template.
     */
    public TimelineTemplate publishTemplate(String templateId) {
        TimelineTemplate template = getTemplate(templateId);
        TimelineTemplate published = template.publish();
        templates.put(templateId, published);
        return published;
    }

    /**
     * Create a new version of a template.
     */
    public TimelineTemplate createNewVersion(String templateId, String timelineJson,
                                              List<String> effectKeys) {
        TimelineTemplate template = getTemplate(templateId);
        String newId = Ids.newId("tmpl");
        TimelineTemplate newVersion = template.createNewVersion(newId, timelineJson, effectKeys);
        templates.put(newId, newVersion);

        log.info("Created new version {} of template {}", newId, templateId);
        return newVersion;
    }

    /**
     * Archive a template.
     */
    public TimelineTemplate archiveTemplate(String templateId) {
        TimelineTemplate template = getTemplate(templateId);
        TimelineTemplate archived = template.archive();
        templates.put(templateId, archived);
        return archived;
    }

    /**
     * Create a render preset.
     */
    public RenderPreset createPreset(String workspaceId, String name, String description,
                                      String creatorId, String format, String resolution,
                                      String profile) {
        String id = Ids.newId("preset");
        RenderPreset preset = RenderPreset.create(id, workspaceId, name, description,
                creatorId, format, resolution, profile);
        presets.put(id, preset);

        log.info("Created render preset {} in workspace {}", id, workspaceId);
        return preset;
    }

    /**
     * Get preset by ID.
     */
    public RenderPreset getPreset(String presetId) {
        RenderPreset preset = presets.get(presetId);
        if (preset == null) {
            throw new IllegalArgumentException("Preset not found: " + presetId);
        }
        return preset;
    }

    /**
     * List presets for a workspace.
     */
    public List<RenderPreset> listPresets(String workspaceId) {
        return presets.values().stream()
                .filter(p -> workspaceId.equals(p.workspaceId()))
                .toList();
    }

    /**
     * Update a render preset.
     */
    public RenderPreset updatePreset(String presetId, String name, String description) {
        RenderPreset preset = getPreset(presetId);
        RenderPreset updated = preset.withDetails(name, description);
        presets.put(presetId, updated);
        return updated;
    }

    /**
     * Update preset render settings.
     */
    public RenderPreset updatePresetSettings(String presetId, String format, String resolution,
                                              String profile, Map<String, Object> settings) {
        RenderPreset preset = getPreset(presetId);
        RenderPreset updated = preset.withSettings(format, resolution, profile, settings);
        presets.put(presetId, updated);
        return updated;
    }
}
