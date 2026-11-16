@ApplicationModule(
        displayName = "User Analytics",
        allowedDependencies = {
                "shared",
                "identity",
                "observability",
                "outbox",
                "audit"
        ,
            "shared :: web"
        }
)
package com.example.platform.analytics;

import org.springframework.modulith.ApplicationModule;
