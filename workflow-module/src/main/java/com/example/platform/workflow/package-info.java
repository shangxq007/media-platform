@org.springframework.modulith.ApplicationModule(
        displayName = "Workflow",
        allowedDependencies = {"policy :: feature-flags", "render :: API", "delivery :: API"}
)
package com.example.platform.workflow;
