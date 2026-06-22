@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "security",
            "app",
            "commerce :: infrastructure",
            "billing :: infrastructure",
            "policy :: feature-flags"
        })
package com.example.platform.production;
