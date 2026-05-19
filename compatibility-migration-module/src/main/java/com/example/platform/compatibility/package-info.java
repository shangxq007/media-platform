@ApplicationModule(
        displayName = "Schema Compatibility & Migration",
        allowedDependencies = {
                "shared-kernel",
                "policy-governance-module",
                "extension-module",
                "audit-compliance-module",
                "outbox-event-module",
                "scheduler-module"
        }
)
package com.example.platform.compatibility;

import org.springframework.modulith.ApplicationModule;
