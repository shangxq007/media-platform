plugins {
    id("java-library")
    id("jacoco")
}

group = "com.example.platform"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

dependencies {
    // MCMV2-C: Media Canonical Model V2 domain module.
    // Dependency direction (frozen): Media Canonical Model ← Timeline/Render/Workflow/AI/
    // Delivery/Adapters. media-module MUST NOT depend on render-module/artifact-module/
    // worker/provider modules. It depends only on shared-kernel (exact time/rate
    // primitives, ArtifactId, Jsons) and typed-schema-module (jOOQ persistence types).

    api(project(":shared-kernel"))
    implementation(project(":typed-schema-module"))

    implementation("org.springframework:spring-tx")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.jooq:jooq")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.withType<Test> {
    systemProperty("project.root.dir", rootProject.projectDir.absolutePath)
    useJUnitPlatform()
}
