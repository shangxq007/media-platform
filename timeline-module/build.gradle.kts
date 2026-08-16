plugins {
    `java-library`
}

group = "com.example.platform"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

dependencies {
    implementation(project(":shared-kernel"))
    implementation(project(":media-module"))
    implementation(project(":audio-module"))
    implementation(project(":font-text-module"))
    // storage-module contract only: ContentDigest pin semantics in
    // MediaStreamSourceBinding (same pattern as artifact-module).
    // ArtifactId used by source bindings lives in shared-kernel (shared.identity).
    implementation(project(":storage-module"))
    // Jackson: canonical serialization visibility (records with @JsonCreator/
    // @JsonValue/@JsonSerialize) + TimelineDocumentJsonSerializer (databind).
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
}
