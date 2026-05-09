package com.example.platform.identity.api.dto;

import com.example.platform.identity.domain.Project;

public record ProjectResponse(
        String id,
        String tenantId,
        String name,
        String description,
        String status,
        java.time.Instant createdAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.id(), project.tenantId(), project.name(),
                project.description(), project.status().name(), project.createdAt());
    }
}
