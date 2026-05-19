package com.example.platform.identity.domain;

import java.time.Instant;

public record RolePermission(
        String id,
        String roleId,
        String permissionId,
        Instant createdAt) {}
