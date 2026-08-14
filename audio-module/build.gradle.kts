plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
}

group = "com.example.platform"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// AUDIO_V2 (A2): canonical Audio Mix authority lives in audio-module (pure domain,
// no Spring beans, no infra). Depends only on shared-kernel. Render -> audio is the
// only consumer direction; audio never depends on render/timeline (no cycle).
dependencies {
    implementation(project(":shared-kernel"))
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
