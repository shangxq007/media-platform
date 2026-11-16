@org.springframework.modulith.ApplicationModule(
        displayName = "Execution",
        allowedDependencies = {
            "audio :: mix"
        ,
            "artifact :: app",
            "colorimage",
            "extension :: domain",
            "graph :: api",
            "graph :: result",
            "render :: renderplan",
            "shared :: capability",
            "shared :: digest",
            "shared :: identity",
            "shared :: time"
        }
)
package com.example.platform.execution;
