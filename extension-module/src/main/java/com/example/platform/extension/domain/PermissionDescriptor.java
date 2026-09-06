package com.example.platform.extension.domain;

/**
 * Provider-neutral permission declaration.
 *
 * <p>Concrete providers declare their own namespaced permissions. This
 * contract validates identifier shape only and owns no provider vocabulary.</p>
 *
 * @param permissionId stable namespaced permission ID
 */
public record PermissionDescriptor(String permissionId) {

    public PermissionDescriptor {
        if (permissionId == null) {
            throw new NullPointerException("permissionId must not be null");
        }
        permissionId = permissionId.trim();
        if (!permissionId.matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+")) {
            throw new IllegalArgumentException("permissionId must be a namespaced identifier");
        }
    }
}
