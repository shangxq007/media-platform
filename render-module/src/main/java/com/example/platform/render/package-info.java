@org.springframework.modulith.ApplicationModule(
        displayName = "Render",
        allowedDependencies = {
            "ai",
            "ai :: API",
            "ai :: domain",
            "ai :: video",
            "entitlement",
            "entitlement :: domain",
            "shared",
            "storage",
            "storage :: API",
            "storage :: domain",
            "workflow",
            "extension",
            "extension :: app",
            "extension :: domain"
        }
)
package com.example.platform.render;
