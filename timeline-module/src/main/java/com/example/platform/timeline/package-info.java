@org.springframework.modulith.ApplicationModule(
        displayName = "Timeline",
        allowedDependencies = {
            "audio :: mix",
            "fonttext :: resolution",
            "fonttext :: text"
        ,
            "artifact :: app",
            "artifact :: contract",
            "artifact :: domain",
            "fonttext :: resource",
            "fonttext :: security",
            "fonttext :: typography",
            "identity :: authorization",
            "media :: identity",
            "media :: model",
            "media :: stream",
            "outbox :: app",
            "outbox :: events",
            "shared :: authorization",
            "shared :: digest",
            "shared :: identity",
            "shared :: time",
            "shared :: web",
            "typedschema :: jooq-tables"
        }
)
package com.example.platform.timeline;
