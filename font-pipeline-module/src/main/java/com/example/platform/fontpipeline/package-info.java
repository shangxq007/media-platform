@org.springframework.modulith.ApplicationModule(
        displayName = "Fontpipeline",
        allowedDependencies = {
            "fonttext :: manifest",
            "fonttext :: resource",
            "fonttext :: security",
            "fonttext :: typography"
        ,
            "sandbox :: API"
        }
)
package com.example.platform.fontpipeline;
