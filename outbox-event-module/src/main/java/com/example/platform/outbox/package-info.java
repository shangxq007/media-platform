@org.springframework.modulith.ApplicationModule(displayName = "Outbox & Events",
        allowedDependencies = {
            "typedschema :: records"
        ,
            "sandbox :: Execution",
            "shared :: web",
            "typedschema :: jooq-tables"
        })
package com.example.platform.outbox;
