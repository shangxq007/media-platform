@org.springframework.modulith.ApplicationModule(
        displayName = "Operation",
        allowedDependencies = {
            "artifact :: app",
            "identity :: authorization",
            "media :: identity",
            "media :: stream",
            "observability :: context",
            "shared :: identity",
            "timeline :: composition",
            "timeline :: revision",
            "timeline :: version",
            "timeline :: revisioncommand",
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
