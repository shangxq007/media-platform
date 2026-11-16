@org.springframework.modulith.ApplicationModule(
        displayName = "Commerce",
        allowedDependencies = {
            "shared",
            "payment :: commerce",
            "billing :: app",
            "billing :: domain",
            "entitlement :: app",
            "entitlement :: domain"
        ,
            "typedschema :: jooq-tables"
        ,
            "shared :: web"
        })
package com.example.platform.commerce;
