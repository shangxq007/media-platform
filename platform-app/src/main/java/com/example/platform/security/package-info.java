@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "identity :: app",
            "identity :: domain",
            "identity :: infrastructure",
            "entitlement :: app"
        })
package com.example.platform.security;
