@org.springframework.modulith.ApplicationModule(
        displayName = "Providerplugin",
        allowedDependencies = {
            "workerfabric :: domain"
        ,
            "shared"
        ,
            "execution :: compatibility",
            "execution :: composition",
            "execution :: provider",
            "execution :: taskgraph",
            "extension :: contracts",
            "extension :: domain",
            "sandbox :: API",
            "sandbox :: Execution",
            "workerfabric :: ProviderRuntime",
            "workerfabric :: providernative",
            "workerfabric :: reuse",
            "workerfabric :: runtime"
        }
)
package com.example.platform.providerplugin;
