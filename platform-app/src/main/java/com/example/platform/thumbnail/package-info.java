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
        }
)
package com.example.platform.thumbnail;
