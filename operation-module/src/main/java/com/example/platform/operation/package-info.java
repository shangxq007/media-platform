@org.springframework.modulith.ApplicationModule(
        displayName = "Operation",
        allowedDependencies = {
            "audio :: mix",
            "fonttext :: diagnostics",
            "fonttext :: resolution",
            "fonttext :: text",
            "timeline :: canonicalmodel",
            "timeline :: clip",
            "timeline :: effect",
            "timeline :: relationship",
            "timeline :: selection",
            "timeline :: temporal"
        ,
            "fonttext :: typography",
            "shared :: authorization",
            "shared :: digest",
            "shared :: time",
            "timeline :: canonical"
        }
)
package com.example.platform.operation;
