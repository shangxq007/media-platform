@org.springframework.modulith.ApplicationModule(
        displayName = "Persistence",
        allowedDependencies = {
            "typedschema :: records"
        ,
            "execution :: binding",
            "execution :: compatibility",
            "execution :: taskgraph",
            "typedschema :: jooq-tables"
        }
)
package com.example.platform.persistence;
