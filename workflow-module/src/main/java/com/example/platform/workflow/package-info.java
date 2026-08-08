@org.springframework.modulith.ApplicationModule(
        displayName = "Workflow",
        // APPD-CHV1: shared authorization contract (shared-kernel) is the sanctioned
        // authorization API for workflow-module, which cannot depend on
        // identity-access-module. The canonical AuthorizationDecisionPort and the
        // frozen workflow-definition permission keys live in shared :: authorization.
        allowedDependencies = {"shared", "policy :: feature-flags", "render :: API", "delivery :: API"}
)
package com.example.platform.workflow;
