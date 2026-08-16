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

    // GCR-1 CORRECTION V1 (app/application services + adapter/persistence layer):
    // timeline-owned application services use Spring annotations (@Service/@Transactional);
    // adapter layer implements Timeline-owned persistence (jOOQ + generated typed-schema
    // tables). Adapter-layer dependency on typed-schema-module explicitly documented per
    // directive §9; domain packages remain free of Spring/jOOQ infrastructure.
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation(project(":typed-schema-module"))
    implementation("org.slf4j:slf4j-api")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation(testFixtures(project(":shared-kernel"))) // FixturePath
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
}
