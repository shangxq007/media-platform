package com.example.platform.product.domain;

/**
 * Actions that can be performed within a workspace.
 * Used for permission checking.
 */
public enum UserAction {

    // Project actions
    CREATE_PROJECT(50),
    EDIT_PROJECT(50),
    DELETE_PROJECT(100),
    VIEW_PROJECT(10),

    // Render actions
    CREATE_RENDER_JOB(50),
    CANCEL_RENDER_JOB(50),
    VIEW_RENDER_HISTORY(10),

    // Template actions
    CREATE_TEMPLATE(50),
    EDIT_TEMPLATE(50),
    DELETE_TEMPLATE(100),
    USE_TEMPLATE(50),

    // Asset actions
    UPLOAD_ASSET(50),
    DELETE_ASSET(50),
    VIEW_ASSETS(10),

    // Workspace actions
    MANAGE_WORKSPACE(100),
    MANAGE_USERS(100),
    VIEW_AUDIT_LOG(100),

    // AI actions
    USE_AI_SUGGESTIONS(50),
    CONFIGURE_AI(100);

    private final int requiredLevel;

    UserAction(int requiredLevel) {
        this.requiredLevel = requiredLevel;
    }

    /**
     * Get the required permission level for this action.
     */
    public int getRequiredLevel() {
        return requiredLevel;
    }

    /**
     * Check if a role can perform this action.
     */
    public boolean canBePerformedBy(UserRole role) {
        return role.getPermissionLevel() >= this.requiredLevel;
    }
}
