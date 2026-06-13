package com.example.platform.product.api;

import com.example.platform.product.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for template management.
 */
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateApi {

    private final TemplateService templateService;

    public TemplateApi(TemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Create a new template.
     */
    @PostMapping
    public TimelineTemplate createTemplate(@RequestBody CreateTemplateRequest request) {
        return templateService.createTemplate(request.workspaceId(), request.name(),
                request.description(), request.category(), request.creatorId(),
                request.timelineJson(), request.effectKeys());
    }

    /**
     * Get template by ID.
     */
    @GetMapping("/{templateId}")
    public TimelineTemplate getTemplate(@PathVariable String templateId) {
        return templateService.getTemplate(templateId);
    }

    /**
     * List templates for a workspace.
     */
    @GetMapping
    public List<TimelineTemplate> listTemplates(@RequestParam String workspaceId) {
        return templateService.listTemplates(workspaceId);
    }

    /**
     * List published templates.
     */
    @GetMapping("/published")
    public List<TimelineTemplate> listPublishedTemplates(@RequestParam String workspaceId) {
        return templateService.listPublishedTemplates(workspaceId);
    }

    /**
     * Publish a template.
     */
    @PostMapping("/{templateId}/publish")
    public TimelineTemplate publishTemplate(@PathVariable String templateId) {
        return templateService.publishTemplate(templateId);
    }

    /**
     * Create a new version of a template.
     */
    @PostMapping("/{templateId}/versions")
    public TimelineTemplate createNewVersion(@PathVariable String templateId,
                                              @RequestBody CreateVersionRequest request) {
        return templateService.createNewVersion(templateId, request.timelineJson(), request.effectKeys());
    }

    /**
     * Archive a template.
     */
    @PostMapping("/{templateId}/archive")
    public TimelineTemplate archiveTemplate(@PathVariable String templateId) {
        return templateService.archiveTemplate(templateId);
    }

    // Request records
    public record CreateTemplateRequest(String workspaceId, String name, String description,
                                         String category, String creatorId, String timelineJson,
                                         List<String> effectKeys) {}
    public record CreateVersionRequest(String timelineJson, List<String> effectKeys) {}
}
