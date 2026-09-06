@org.springframework.modulith.ApplicationModule(
        displayName = "Identity & Access",
        allowedDependencies = {"shared", "entitlement :: API", "entitlement :: app", "entitlement :: domain"})
package com.example.platform.identity;
