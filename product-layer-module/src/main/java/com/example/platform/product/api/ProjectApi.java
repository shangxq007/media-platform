package com.example.platform.product.api;

import com.example.platform.product.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for project management.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectApi {

    private final ProjectService projectService;

    public ProjectApi(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Create a new project.
     */
    @PostMapping
    public Project createProject(@RequestBody CreateProjectRequest request) {
        return projectService.createProject(request.workspaceId(), request.name(),
                request.description(), request.ownerId());
    }

    /**
     * Create a project from a template.
     */
    @PostMapping("/from-template")
    public Project createProjectFromTemplate(@RequestBody CreateFromTemplateRequest request) {
        return projectService.createProjectFromTemplate(request.workspaceId(), request.name(),
                request.description(), request.ownerId(), request.templateId());
    }

    /**
     * Get project by ID.
     */
    @GetMapping("/{projectId}")
    public Project getProject(@PathVariable String projectId) {
        return projectService.getProject(projectId);
    }

    /**
     * List projects for a workspace.
     */
    @GetMapping
    public List<Project> listProjects(@RequestParam String workspaceId) {
        return projectService.listProjects(workspaceId);
    }

    /**
     * Update project details.
     */
    @PutMapping("/{projectId}")
    public Project updateProject(@PathVariable String projectId,
                                  @RequestBody UpdateProjectRequest request) {
        return projectService.updateProject(projectId, request.name(), request.description());
    }

    /**
     * Update project timeline.
     */
    @PostMapping("/{projectId}/timeline")
    public Project updateTimeline(@PathVariable String projectId,
                                   @RequestBody UpdateTimelineRequest request) {
        return projectService.updateTimeline(projectId, request.timelineSnapshotId());
    }

    /**
     * Mark project as in progress.
     */
    @PostMapping("/{projectId}/start")
    public Project markInProgress(@PathVariable String projectId) {
        return projectService.markInProgress(projectId);
    }

    /**
     * Mark project as completed.
     */
    @PostMapping("/{projectId}/complete")
    public Project markCompleted(@PathVariable String projectId) {
        return projectService.markCompleted(projectId);
    }

    /**
     * Archive project.
     */
    @PostMapping("/{projectId}/archive")
    public Project archiveProject(@PathVariable String projectId) {
        return projectService.archiveProject(projectId);
    }

    // Request records
    public record CreateProjectRequest(String workspaceId, String name, String description, String ownerId) {}
    public record CreateFromTemplateRequest(String workspaceId, String name, String description,
                                             String ownerId, String templateId) {}
    public record UpdateProjectRequest(String name, String description) {}
    public record UpdateTimelineRequest(String timelineSnapshotId) {}
}
