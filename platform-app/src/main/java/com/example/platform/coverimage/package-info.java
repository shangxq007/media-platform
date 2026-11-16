@org.springframework.modulith.ApplicationModule(
        displayName = "Coverimage",
        allowedDependencies = {
            "config :: mediaexecution",
            "contract :: media"
        ,
            "shared"
        ,
            "execution :: admission"
        }
)
package com.example.platform.coverimage;
