package com.example.platform.identity.api.dto;

import com.example.platform.identity.domain.Project;

@org.springframework.modulith.NamedInterface("projects")
public record ProjectResponse(
        String id,
        String tenantId,
        String name,
        String description,
        String status,
        java.time.Instant createdAt, String workspaceId) {
    public ProjectResponse(String id,String tenantId,String name,String description,String status,java.time.Instant createdAt) {
        this(id,tenantId,name,description,status,createdAt,null);
    }

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.id(), project.tenantId(), project.name(),
                project.description(), project.status().name(), project.createdAt(), project.workspaceId());
    }
}
