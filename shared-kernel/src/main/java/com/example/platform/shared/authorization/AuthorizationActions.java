package com.example.platform.shared.authorization;

/**
 * Sealed, typed catalogue of the W2 workflow-definition security actions mapped to
 * their existing/seeded permission keys.
 *
 * <p>These are the ONLY permission keys the W2 authorization boundary may request,
 * and they mirror the constants declared in
 * {@link com.example.platform.workflow.definition.api.UserWorkflowDefinitionController}:
 * {@code workflow-definition.edit}, {@code workflow-definition.publish},
 * {@code workflow-definition.archive}, {@code workflow-definition.read}. The keys not
 * previously present in the RBAC seed are added to
 * {@code identity-access-module BuiltinDataInitializer} in this same slice.</p>
 *
 * <p>Because these are typed enum constants, services cannot pass arbitrary
 * user-supplied strings into the authorization layer.</p>
 */
public enum AuthorizationActions {

    WORKFLOW_DEFINITION_READ("workflow-definition.read", AuthorizationResourceType.WORKFLOW_DEFINITION, "Read workflow definition"),
    WORKFLOW_DEFINITION_EDIT("workflow-definition.edit", AuthorizationResourceType.WORKFLOW_DEFINITION, "Create/edit workflow definition"),
    WORKFLOW_DEFINITION_PUBLISH("workflow-definition.publish", AuthorizationResourceType.WORKFLOW_DEFINITION, "Publish workflow definition"),
    WORKFLOW_DEFINITION_ARCHIVE("workflow-definition.archive", AuthorizationResourceType.WORKFLOW_DEFINITION, "Archive workflow definition"),

    // UWEV1-FV1 (UWE-ADR-023): execution permissions
    WORKFLOW_EXECUTION_START("workflow.execution.start", AuthorizationResourceType.WORKFLOW_EXECUTION, "Start workflow execution"),
    WORKFLOW_EXECUTION_READ("workflow.execution.read", AuthorizationResourceType.WORKFLOW_EXECUTION, "Read workflow execution"),
    WORKFLOW_EXECUTION_CANCEL("workflow.execution.cancel", AuthorizationResourceType.WORKFLOW_EXECUTION, "Cancel workflow execution"),
    WORKFLOW_EXECUTION_APPROVE("workflow.execution.approve", AuthorizationResourceType.WORKFLOW_EXECUTION, "Approve/reject workflow execution");

    private final AuthorizationAction action;

    AuthorizationActions(String permissionKey, AuthorizationResourceType resourceType, String humanReadableName) {
        this.action = new AuthorizationAction(permissionKey, resourceType, humanReadableName);
    }

    public AuthorizationAction action() {
        return action;
    }

    public String permissionKey() {
        return action.permissionKey();
    }
}
