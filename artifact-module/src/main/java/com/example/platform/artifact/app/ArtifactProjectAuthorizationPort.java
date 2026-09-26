package com.example.platform.artifact.app;

/**
 * Owner-published project-scoped authorization boundary for the Artifact HTTP
 * surfaces (ARTIFACT_AUTHORITY_CONTRACT_V1, "tenant/project scope").
 *
 * <p>Every Artifact REST controller must consult this boundary BEFORE it hydrates
 * or mutates data. The implementation is provided by the composition root
 * ({@code platform-app}) and delegates to the canonical Identity
 * {@code AuthorizationDecisionPort}; the Artifact module never performs its own
 * authorization decision (Layer A gate = canonical RBAC port only).</p>
 *
 * <p>Fail-closed contract: implementations must reject a missing/blank
 * tenant/project, a mismatch between the explicit and ambient tenant, a missing
 * authenticated actor, and any RBAC denial.</p>
 */
public interface ArtifactProjectAuthorizationPort {

    /** Authorize a project-scoped Artifact read; throws when denied. */
    void requireRead(String tenantId, String projectId);

    /** Authorize a project-scoped Artifact lifecycle mutation; throws when denied. */
    void requireWrite(String tenantId, String projectId);
}
