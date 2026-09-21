@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"audit-ports :: api",
            "shared",
            "identity :: authorization",
            "identity :: account",
            "observability :: context",
            "identity :: app",
            "identity :: domain",
            "identity :: infrastructure",
            "entitlement :: app"
        })
package com.example.platform.security;
