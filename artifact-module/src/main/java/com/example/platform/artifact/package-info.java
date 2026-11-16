@org.springframework.modulith.ApplicationModule(
        displayName = "Artifact",
        allowedDependencies = {
            "typedschema :: records"
        ,
            "audit-ports :: api",
            "outbox :: app",
            "outbox :: events",
            "shared :: digest",
            "shared :: identity",
            "shared :: time",
            "shared :: web",
            "storage :: API",
            "storage :: contract",
            "typedschema :: jooq-tables"
        }
)
@org.springframework.modulith.NamedInterface("catalog")
package com.example.platform.artifact;
