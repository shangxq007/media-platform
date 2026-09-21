@org.springframework.modulith.ApplicationModule(
        displayName = "Identity & Access",
        allowedDependencies = {"audit-ports :: api", "shared", "observability :: context", "entitlement :: API", "entitlement :: app", "entitlement :: domain"})
package com.example.platform.identity;
