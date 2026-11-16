@org.springframework.modulith.ApplicationModule(
        displayName = "Thumbnail",
        allowedDependencies = {
            "config :: mediaexecution",
            "contract :: media"
        ,
            "shared"
        ,
            "artifact :: app",
            "execution :: admission"
        ,
            "shared :: digest",
            "shared :: identity"
        }
)
package com.example.platform.thumbnail;
