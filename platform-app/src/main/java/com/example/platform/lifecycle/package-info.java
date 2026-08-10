@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "workflow :: temporal",
            "outbox :: app",
            "extension",
            "extension :: runtime",
            "extension :: app"
        })
package com.example.platform.lifecycle;
