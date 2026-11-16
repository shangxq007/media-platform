@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"shared", "identity :: authorization", "artifact :: app", "artifact :: domain", "render :: app", "render :: API", "render :: timeline",
            "shared :: identity",
            "shared :: web"
        ,
            "shared :: authorization"
        })
package com.example.platform.delivery;
