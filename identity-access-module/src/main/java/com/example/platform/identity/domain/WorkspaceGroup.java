package com.example.platform.identity.domain;

import java.time.Instant;

public record WorkspaceGroup(
        String id,
        String workspaceId,
        String name,
        String description,
        Instant createdAt) {}
