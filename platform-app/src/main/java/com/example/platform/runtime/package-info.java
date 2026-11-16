@org.springframework.modulith.ApplicationModule(
        displayName = "Runtime",
        allowedDependencies = {
            "persistence :: binding",
            "bmfprovider",
            "workerfabric :: admission",
            "workerfabric :: domain",
            "workerfabric :: providernative",
            "workerfabric :: reuse",
            "workflow :: mediatask"
        ,
            "config",
            "datasource",
            "lifecycle",
            "production",
            "providerplugin",
            "shared"
        ,
            "artifact :: app",
            "artifact :: domain",
            "execution :: admission",
            "execution :: binding",
            "execution :: compatibility",
            "execution :: planning",
            "execution :: provider",
            "execution :: taskgraph",
            "extension :: contracts",
            "render :: API",
            "sandbox :: API",
            "storage :: API",
            "storage :: contract",
            "storage :: infrastructure",
            "workflow :: temporal"
        }
)
package com.example.platform.runtime;
