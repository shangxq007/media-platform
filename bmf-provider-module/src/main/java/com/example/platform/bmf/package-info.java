/** Fail-closed provider-private BMF CPU integration seams. */
@org.springframework.modulith.ApplicationModule(
        displayName = "Bmf",
        allowedDependencies = {
            "render :: renderplan",
            "workerfabric :: providernative"
        ,
            "shared"
        ,
            "execution :: compatibility",
            "execution :: composition",
            "execution :: domain",
            "execution :: planning",
            "execution :: provider",
            "execution :: taskgraph",
            "extension :: domain",
            "sandbox :: API",
            "sandbox :: Execution",
            "workerfabric :: ProviderRuntime",
            "workerfabric :: domain",
            "workerfabric :: reuse",
            "workerfabric :: runtime"
        ,
            "shared :: time"
        ,
            "shared :: capability"
        }
)
package com.example.platform.bmf;
