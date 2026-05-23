@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"shared", "secrets :: API", "storage :: domain"})
package com.example.platform.delivery;
