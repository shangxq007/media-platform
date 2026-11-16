@org.springframework.modulith.ApplicationModule(
        displayName = "Identity & Access",
        allowedDependencies = {"audit-ports :: api", "shared", "observability :: context", "entitlement :: API", "entitlement :: app", "entitlement :: domain",
            "storage :: contract",
            "typedschema :: jooq-tables"
        ,
            "shared :: web"
        ,
            "shared :: authorization"
        })
package com.example.platform.identity;
