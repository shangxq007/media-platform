@org.springframework.modulith.ApplicationModule(
        displayName = "Federation Query",
        allowedDependencies = {
            "shared",
            "identity :: app", "identity :: workspace",
            "identity :: domain",
            "identity :: infrastructure",
            "render",
            "render :: API",
            "render :: app",
            "render :: domain",
            "render :: infrastructure",
            "extension :: contracts",
            "extension :: domain",
            "billing :: app",
            "billing :: domain",
            "billing :: usage",
            "entitlement :: API",
            "entitlement :: domain",
            "prompt :: app",
            "prompt :: domain",
            "ai :: API",
            "ai :: domain",
            "policy :: feature-flags",
            "observability :: monitoring"
        })
package com.example.platform.federation;
