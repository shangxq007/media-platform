@org.springframework.modulith.ApplicationModule(
        displayName = "Commerce",
        allowedDependencies = {
            "shared",
            "payment :: commerce",
            "billing :: app",
            "billing :: domain",
            "entitlement :: app",
            "entitlement :: domain"
        })
package com.example.platform.commerce;
