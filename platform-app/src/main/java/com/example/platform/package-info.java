@org.springframework.modulith.ApplicationModule(
        displayName = "Platform",
        allowedDependencies = {
            "analytics",
            "cloudresource",
            "datasource",
            "identity :: app",
            "ingest :: config",
            "ingest :: contract",
            "ingest :: tika",
            "lifecycle",
            "marketplace",
            "observability :: monitoring",
            "outbox :: app",
            "persistence :: binding",
            "runtime",
            "runtime :: mediatask",
            "usage :: app",
            "usage :: events",
            "usage :: infrastructure",
            "usage :: observations"
        }
)
package com.example.platform;
