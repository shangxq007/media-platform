@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"audit-ports :: api", "shared", "outbox :: app", "outbox :: events", "identity :: authorization", "secrets :: API", "storage :: domain", "storage :: contract", "artifact :: app", "artifact :: domain", "render :: requests", "render :: events"})
package com.example.platform.delivery;
