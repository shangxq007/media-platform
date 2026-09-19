@org.springframework.modulith.ApplicationModule(displayName="Workflow", allowedDependencies={
        "shared", "identity :: authorization", "identity :: projects", "observability :: context", "policy :: feature-flags",
        "render :: API", "delivery :: API", "extension :: contracts", "extension :: domain", "operation :: invocation", "timeline :: composition", "outbox :: app", "outbox :: events"})
package com.example.platform.workflow;
