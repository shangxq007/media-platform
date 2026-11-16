@org.springframework.modulith.ApplicationModule(
        displayName = "Ingest",
        allowedDependencies = {
            "typedschema :: records"
        ,
            "media :: assets",
            "render :: API",
            "sandbox :: API",
            "storage :: contract",
            "storage :: domain",
            "typedschema :: jooq-tables"
        }
)
package com.example.platform.ingest;
