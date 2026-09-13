@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"shared", "outbox :: app", "outbox :: events", "identity :: authorization", "secrets :: API", "storage :: domain", "artifact :: app", "artifact :: domain", "render :: requests", "render :: events"})
package com.example.platform.delivery;
