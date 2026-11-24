plugins {
    id("java-library")
}

group = "com.example.platform"
version = "1.1.0"

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
    // The contribution implements the generic provider-plugin SPI, so the host SPI is part of this
    // module's public surface for the composition root.
    api(project(":provider-plugin-runtime-module"))
    implementation(project(":bmf-provider-module"))
    implementation(project(":media-execution-plan-module"))
    implementation(project(":worker-fabric-module"))
    implementation(project(":sandbox-isolation-module"))
    implementation(project(":extension-module"))
    implementation(project(":shared-kernel"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
