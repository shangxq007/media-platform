@org.springframework.modulith.ApplicationModule(
        displayName = "Media",
        allowedDependencies = {
            "typedschema :: records"
        ,
            "colorimage",
            "shared :: identity",
            "shared :: time",
            "typedschema :: jooq-tables"
        }
)
package com.example.platform.media;
