@org.springframework.modulith.ApplicationModule(
        displayName = "Render",
        allowedDependencies = {
            "ai",
            "ai :: API",
            "ai :: domain",
            "ai :: video",
            "billing :: app",
            "billing :: domain",
            "billing :: usage",
            "entitlement",
            "entitlement :: domain",
            "shared",
            "storage",
            "storage :: API",
            "storage :: domain",
            "workflow",
            "extension",
            "extension :: app",
            "extension :: domain",
            "media",
            "audio",
            "sandbox :: API"
        }
)
package com.example.platform.render;
