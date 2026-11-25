plugins { id("java-library") }

// Compiled #20 contracts, including their canonical codecs. The planner and verified authoring adapters live in Render.
dependencies {
    api(project(":shared-kernel"))
    api(project(":extension-module"))
    api(project(":timeline-module"))
    api(project(":font-text-module"))
    api(project(":color-image-module"))
    implementation(project(":platform-algorithms:graph"))
    implementation(project(":audio-module"))
}
