@org.springframework.modulith.ApplicationModule(
        displayName = "Workflow",
        // APPD-CHV1: shared authorization contract (shared-kernel) is the sanctioned
        // authorization API for workflow-module, which cannot depend on
        // identity-access-module. The canonical AuthorizationDecisionPort and the
        // frozen workflow-definition permission keys live in shared :: authorization.
        // UWEV1-FV1 (UWE-ADR-025): workflow depends on extension::runtime
        // (PluginRuntime effect execution) and billing::usage (CanonicalActorRef /
        // OperationRef — EUMF canonical types). These are the narrow sanctioned
        // surfaces; never runtime internals / provider SPI / sandbox engine.
        allowedDependencies = {"shared", "policy :: feature-flags", "render :: API", "delivery :: API",
            "billing :: usage", "extension :: runtime"}
)
package com.example.platform.workflow;
