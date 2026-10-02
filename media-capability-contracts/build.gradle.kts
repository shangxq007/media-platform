plugins { id("java-library") }

// PROVIDER-MERGE-1-R2: provider-neutral media capability contracts shared by the platform-app
// frame-extract slice and the ffmpeg typed provider chain. Pure Java: no Spring, no framework.
dependencies {
    api(project(":shared-kernel"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
