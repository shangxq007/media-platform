package com.example.platform.shared.authorization;

/**
 * Bounded set of resource types known to the authorization model.
 *
 * <p>Kept deliberately small and explicit — authorization decisions are scoped to
 * these resource types. Each maps to a concrete relation in the RBAC/tenant model.</p>
 */
public enum AuthorizationResourceType {

    WORKFLOW_DEFINITION,
    WORKFLOW_EXECUTION,
    RENDER_JOB,
    PROJECT,
    WORKSPACE,
    TENANT,
    EXTENSION,
    BILLING,
    AUDIT,
    GENERIC
}
