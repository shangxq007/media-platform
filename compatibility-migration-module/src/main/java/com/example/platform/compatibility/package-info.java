@ApplicationModule(
        displayName = "Schema Compatibility & Migration",
        allowedDependencies = {
                "shared",
                "policy",
                "extension",
                "audit",
                "outbox",
                "scheduler"
        }
)
package com.example.platform.compatibility;

import org.springframework.modulith.ApplicationModule;
