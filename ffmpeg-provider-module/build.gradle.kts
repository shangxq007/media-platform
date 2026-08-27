plugins {
    id("java-library")
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
    implementation(project(":media-execution-plan-module"))
    implementation(project(":worker-fabric-module"))
    implementation(project(":sandbox-isolation-module"))
    // Compile-only ABI companions exposed by canonical media-execution public record signatures.
    compileOnly(project(":extension-module"))
    compileOnly(project(":render-module"))

    testImplementation(project(":artifact-module"))
    testImplementation(project(":storage-module"))
    testImplementation(project(":render-module"))
    testImplementation(project(":shared-kernel"))
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
