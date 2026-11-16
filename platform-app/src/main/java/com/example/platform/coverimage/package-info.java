@org.springframework.modulith.ApplicationModule(
        displayName = "Coverimage",
        allowedDependencies = {
            "config :: mediaexecution",
            "contract :: media"
        ,
            "shared"
        ,
            "execution :: admission"
        ,
            "shared :: web"
        }
)
package com.example.platform.coverimage;
