@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "secrets :: API",
            "identity :: app",
            "ai :: domain"
        ,
            "typedschema :: jooq-tables"
        ,
            "shared :: web"
        })
package com.example.platform.app;
