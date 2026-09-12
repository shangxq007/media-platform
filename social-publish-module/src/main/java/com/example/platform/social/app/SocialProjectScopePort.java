package com.example.platform.social.app;

/** Resolves canonical Project tenant ownership without exposing identity persistence internals. */
@FunctionalInterface
public interface SocialProjectScopePort {
    boolean belongsToTenant(String tenantId, String projectId);
}
