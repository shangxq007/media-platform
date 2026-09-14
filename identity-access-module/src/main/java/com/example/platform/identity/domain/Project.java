package com.example.platform.identity.domain;

import java.time.Instant;

public record Project(
        String id,
        String tenantId,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt,
        String workspaceId) {

    public Project(String id,String tenantId,String name,String description,ProjectStatus status,Instant createdAt) {
        this(id,tenantId,name,description,status,createdAt,null);
    }

    public enum ProjectStatus {
        ACTIVE, ARCHIVED
    }
}
