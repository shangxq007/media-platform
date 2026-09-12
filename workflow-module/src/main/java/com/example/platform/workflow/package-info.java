@org.springframework.modulith.ApplicationModule(
        displayName = "Workflow",
        // Identity owns the published authorization contract; Workflow owns its action vocabulary.
        // UWEV1-FV1 (UWE-ADR-025): workflow depends on extension::runtime
        // (PluginRuntime effect execution) and shared neutral usage references. These are
        // the narrow sanctioned surfaces; never runtime internals / provider SPI / sandbox engine.
        allowedDependencies = {"shared", "identity :: authorization", "policy :: feature-flags", "render :: API", "delivery :: API",
            "extension :: runtime", "operation :: invocation"}
)
package com.example.platform.workflow;
