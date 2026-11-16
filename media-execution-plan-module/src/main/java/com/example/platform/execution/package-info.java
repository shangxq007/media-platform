@org.springframework.modulith.ApplicationModule(
        displayName = "Execution",
        allowedDependencies = {
            "audio :: mix"
        ,
            "artifact :: app",
            "colorimage",
            "extension :: domain",
            "render :: renderplan",
            "shared :: capability",
            "shared :: digest",
            "shared :: identity",
            "shared :: time"
,
            "graph :: api",
            "graph :: result"
        }
)
package com.example.platform.execution;
