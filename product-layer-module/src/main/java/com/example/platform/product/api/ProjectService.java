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
 * Service for project management.
 */
@Service
@Transactional
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    // In-memory storage (replace with repository in production)
    private final Map<String, Project> projects = new ConcurrentHashMap<>();

    /**
     * Create a new project.
     */
    public Project createProject(String workspaceId, String name, String description, String ownerId) {
        String id = Ids.newId("proj");
        Project project = Project.create(id, workspaceId, name, description, ownerId);
        projects.put(id, project);

        log.info("Created project {} in workspace {}", id, workspaceId);
        return project;
    }

    /**
     * Create a project from a template.
     */
    public Project createProjectFromTemplate(String workspaceId, String name, String description,
                                              String ownerId, String templateId) {
        String id = Ids.newId("proj");
        Project project = Project.fromTemplate(id, workspaceId, name, description, ownerId, templateId);
        projects.put(id, project);

        log.info("Created project {} from template {} in workspace {}", id, templateId, workspaceId);
        return project;
    }

    /**
     * Get project by ID.
     */
    public Project getProject(String projectId) {
        Project project = projects.get(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        return project;
    }

    /**
     * List projects for a workspace.
     */
    public List<Project> listProjects(String workspaceId) {
        return projects.values().stream()
                .filter(p -> workspaceId.equals(p.workspaceId()))
                .toList();
    }

    /**
     * Update project details.
     */
    public Project updateProject(String projectId, String name, String description) {
        Project project = getProject(projectId);
        Project updated = project.withDetails(name, description);
        projects.put(projectId, updated);
        return updated;
    }

    /**
     * Update project timeline.
     */
    public Project updateTimeline(String projectId, String timelineSnapshotId) {
        Project project = getProject(projectId);
        Project updated = project.withTimeline(timelineSnapshotId);
        projects.put(projectId, updated);
        return updated;
    }

    /**
     * Mark project as in progress.
     */
    public Project markInProgress(String projectId) {
        Project project = getProject(projectId);
        Project updated = project.markInProgress();
        projects.put(projectId, updated);
        return updated;
    }

    /**
     * Mark project as completed.
     */
    public Project markCompleted(String projectId) {
        Project project = getProject(projectId);
        Project updated = project.markCompleted();
        projects.put(projectId, updated);
        return updated;
    }

    /**
     * Archive project.
     */
    public Project archiveProject(String projectId) {
        Project project = getProject(projectId);
        Project archived = project.archive();
        projects.put(projectId, archived);
        return archived;
    }
}
