@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "secrets :: API",
            "identity :: app",
            "ai :: domain"
        })
package com.example.platform.app;
