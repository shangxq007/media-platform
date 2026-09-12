@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"shared", "identity :: authorization", "secrets :: API", "storage :: domain"})
package com.example.platform.delivery;
