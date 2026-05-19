package com.example.platform.identity.domain;

import java.time.Instant;

public record Role(
        String id,
        String roleKey,
        String name,
        String description,
        RoleScope scope,
        Instant createdAt) {

    public enum RoleScope {
        GLOBAL, WORKSPACE
    }
}
