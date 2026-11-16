@org.springframework.modulith.ApplicationModule(
        displayName = "Federation Query",
        allowedDependencies = {"audit-ports :: api",
            "usage :: observations",
            "shared", "identity :: reads", "identity :: authorization", "identity :: projects", "billing :: reads", "prompt :: reads",
             "identity :: workspace",
            "render",
            "render :: API",
            "extension :: contracts",
            "entitlement :: API",
            "entitlement :: domain",
            "ai :: API",
            "ai :: domain",
            "policy :: feature-flags",
            "observability :: monitoring"
        ,
            "shared :: web"
        ,
            "shared :: authorization"
        })
package com.example.platform.federation;
