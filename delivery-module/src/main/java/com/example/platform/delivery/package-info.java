@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"shared", "identity :: authorization", "secrets :: API", "storage :: domain", "artifact :: app", "artifact :: domain", "render :: events"})
package com.example.platform.delivery;
