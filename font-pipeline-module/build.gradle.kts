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

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
