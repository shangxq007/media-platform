package com.example.platform.product.domain;

/**
 * User roles within a workspace.
 * Controls access levels and permissions.
 */
public enum UserRole {

    /**
     * Full workspace administration capabilities.
     * Can manage users, settings, billing, and all resources.
     */
    ADMIN(100),

    /**
     * Can create and edit projects, templates, and render jobs.
     * Cannot manage users or workspace settings.
     */
    EDITOR(50),

    /**
     * Can view projects and render history.
     * Cannot create or modify resources.
     */
    VIEWER(10),

    /**
     * API-only access for integrations.
     * Permissions defined by API key scope.
     */
    API(30);

    private final int permissionLevel;

    UserRole(int permissionLevel) {
        this.permissionLevel = permissionLevel;
    }

    /**
     * Get the permission level (higher = more permissions).
     */
    public int getPermissionLevel() {
        return permissionLevel;
    }

    /**
     * Check if this role can perform the given action.
     */
    public boolean canPerform(UserAction action) {
        return this.permissionLevel >= action.getRequiredLevel();
    }

    /**
     * Check if this role has admin privileges.
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * Check if this role can edit resources.
     */
    public boolean canEdit() {
        return this == ADMIN || this == EDITOR;
    }

    /**
     * Check if this role can only view.
     */
    public boolean isViewOnly() {
        return this == VIEWER;
    }
}
