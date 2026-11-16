@org.springframework.modulith.ApplicationModule(
        displayName = "Entitlement",
        allowedDependencies = {"audit-ports :: api", "shared", "policy :: feature-flags",
            "typedschema :: jooq-tables"
        ,
            "shared :: web"
        })
package com.example.platform.entitlement;
