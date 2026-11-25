@org.springframework.modulith.ApplicationModule(
        displayName = "Config",
        allowedDependencies = {
            "media :: probe",
            "media :: stream",
            "bmfprovider",
            "render :: renderplan",
            "timeline :: app",
            "timeline :: canonical",
            "timeline :: effect",
            "timeline :: version",
            "workflow :: mediatask"
        ,
            "providerplugin",
            "providerplugin :: execution",
            "shared"
        ,
            "audio :: mix",
            "shared :: digest",
            "artifact :: app",
            "artifact :: domain",
            "composition :: app",
            "execution :: admission",
            "execution :: binding",
            "execution :: compatibility",
            "execution :: domain",
            "execution :: planning",
            "execution :: provider",
            "execution :: taskgraph",
            "extension :: contracts",
            "extension :: domain",
            "identity :: authorization",
            "identity :: projects",
            "notification :: event",
            "notification :: events",
            "notification :: ingress",
            "outbox :: app",
            "outbox :: events",
            "providerplugin :: Execution",
            "render :: API",
            "render :: app",
            "render :: requests",
            "render :: timeline",
            "sandbox :: API",
            "sandbox :: Execution",
            "storage :: API",
            "timeline :: revision"
        ,
            "typedschema :: jooq-tables"
        ,
            "shared :: identity",
            "shared :: time",
            "shared :: web"
        ,
            "shared :: authorization",
            "shared :: capability"
        }
)
package com.example.platform.config;
