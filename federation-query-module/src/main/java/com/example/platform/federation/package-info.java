@org.springframework.modulith.ApplicationModule(
        displayName = "Federation Query",
        allowedDependencies = {
            "shared",
            "identity :: app",
            "identity :: domain",
            "identity :: infrastructure",
            "render",
            "render :: API",
            "render :: app",
            "render :: domain",
            "render :: infrastructure",
            "extension :: app",
            "extension :: domain",
            "billing :: app",
            "billing :: domain",
            "entitlement :: app",
            "entitlement :: domain",
            "prompt :: app",
            "prompt :: domain",
            "ai :: API",
            "ai :: domain",
            "policy :: feature-flags"
        })
package com.example.platform.federation;
