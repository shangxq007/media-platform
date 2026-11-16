/**
 * BMF provider-plugin integration boundary.
 *
 * <p>Owns the in-process {@code ProviderPluginContribution} that connects the pure BMF engine module
 * to the generic provider-plugin host. The host never depends on this module or on BMF; the
 * composition root opts into the bridge when {@code platform.bmf-provider.enabled=true}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "BMF Provider Bridge",
        allowedDependencies = {
            "bmf",
            "execution",
            "execution :: compatibility",
            "execution :: provider",
            "extension",
            "extension :: domain",
            "providerplugin",
            "sandbox",
            "sandbox :: Execution",
            "shared",
            "workerfabric",
            "workerfabric :: ProviderRuntime",
            "workerfabric :: domain",
            "workerfabric :: providernative",
            "workerfabric :: runtime"
        ,
            "shared :: capability"
        }
)
package com.example.platform.bmfprovider;
