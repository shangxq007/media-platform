plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(project(":font-text-module"))
    // V1 font metadata adapter. Apache FontBox is a pure-Java font parser (no native code).
    // Version is pinned inline: the Spring Boot / Spring AI / Testcontainers BOMs do not
    // manage org.apache.pdfbox:* (verified in the Phase 2 governance probe).
    implementation("org.apache.pdfbox:fontbox:3.0.8")
    // fontbox transitively brings pdfbox-io and commons-logging from its POM.

    // Phase 3: LWJGL FreeType for variable-font axes and color-font capabilities.
    // Shaping (lwjgl-harfbuzz) is intentionally out of scope.
    implementation("org.lwjgl:lwjgl:3.3.6")
    implementation("org.lwjgl:lwjgl-freetype:3.3.6")
    runtimeOnly("org.lwjgl:lwjgl:3.3.6:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-freetype:3.3.6:natives-linux")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // LWJGL loads native libraries via System.load; Java 25 restricts that without native access.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
