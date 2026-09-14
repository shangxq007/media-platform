package com.example.platform.identity.api.project;
/** Authoritative resource relationship; this value is not a permission grant. */
public record ProjectScope(String tenantId, String workspaceId, String projectId) {
    public ProjectScope {
        if(tenantId==null||workspaceId==null||projectId==null)throw new IllegalArgumentException("Resolved Project scope required");
    }
}
