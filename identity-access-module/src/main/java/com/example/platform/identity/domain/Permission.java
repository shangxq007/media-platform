package com.example.platform.identity.domain;

import java.time.Instant;

public record Permission(
        String id,
        String permissionKey,
        String name,
        String description,
        String resourceType,
        Instant createdAt) {}
