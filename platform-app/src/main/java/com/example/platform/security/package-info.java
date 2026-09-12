@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "identity :: authorization",
            "observability :: context",
            "identity :: app",
            "identity :: domain",
            "identity :: infrastructure",
            "entitlement :: app"
        })
package com.example.platform.security;
