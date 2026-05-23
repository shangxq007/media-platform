@org.springframework.modulith.ApplicationModule(
        displayName = "Render",
        allowedDependencies = {
            "ai :: API",
            "ai :: domain",
            "shared",
            "storage :: API",
            "storage :: domain",
            "workflow",
            "extension :: app",
            "extension :: domain"
        }
)
package com.example.platform.render;
