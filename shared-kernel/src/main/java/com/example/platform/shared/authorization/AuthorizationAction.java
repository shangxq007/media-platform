package com.example.platform.shared.authorization;

import java.util.Objects;

/**
 * A typed authorization action, validated against the frozen permission vocabulary.
 *
 * <p>Unlike a raw string, an {@code AuthorizationAction} carries its permission-key
 * and a bounded {@link AuthorizationResourceType}. Callers construct these from the
 * sealed, typed permission keys seeded by the identity-access authority (e.g. via
 * {@link AuthorizationActions}) — they are never built from arbitrary user-supplied
 * strings. The concrete permission key is the single source of truth consumed by the
 * RBAC adapter.</p>
 *
 * @param permissionKey     the exact permission key (must match a seeded key)
 * @param resourceType      the resource type this action applies to
 * @param humanReadableName descriptive name for audit/diagnostic use
 */
public record AuthorizationAction(
        String permissionKey,
        AuthorizationResourceType resourceType,
        String humanReadableName) {

    public AuthorizationAction {
        if (permissionKey == null || permissionKey.isBlank()) {
            throw new IllegalArgumentException("permissionKey must not be blank");
        }
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (humanReadableName == null || humanReadableName.isBlank()) {
            throw new IllegalArgumentException("humanReadableName must not be blank");
        }
    }
}
