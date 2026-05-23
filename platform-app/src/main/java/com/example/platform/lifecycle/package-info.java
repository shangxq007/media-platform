@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "workflow :: temporal",
            "outbox :: app",
            "sandbox :: app",
            "extension :: app"
        })
package com.example.platform.lifecycle;
