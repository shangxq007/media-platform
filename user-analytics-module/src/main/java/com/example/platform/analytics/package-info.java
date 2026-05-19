@ApplicationModule(
        displayName = "User Analytics",
        allowedDependencies = {
                "shared",
                "identity",
                "observability",
                "outbox",
                "audit"
        }
)
package com.example.platform.analytics;

import org.springframework.modulith.ApplicationModule;
