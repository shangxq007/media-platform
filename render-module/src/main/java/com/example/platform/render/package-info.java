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
            "entitlement :: commercial",
            "shared",
            "identity :: authorization", "identity :: projects",
            "storage",
            "storage :: API",
            "storage :: domain", "storage :: contract", "artifact :: app", "artifact :: domain",
            "outbox :: app", "outbox :: events",
            "workflow",
            "extension",
            "extension :: app",
            "extension :: domain",
            "media",
            "audio",
            "operation :: invocation",
            "sandbox :: API", "sandbox :: Execution"
        }
)
package com.example.platform.render;
