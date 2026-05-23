@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "commerce :: infrastructure",
            "billing :: infrastructure",
            "policy :: feature-flags"
        })
package com.example.platform.production;
