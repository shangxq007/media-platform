@org.springframework.modulith.ApplicationModule(
        displayName = "Entitlement",
        allowedDependencies = {"audit-ports :: api", "shared", "policy :: feature-flags"})
package com.example.platform.entitlement;
