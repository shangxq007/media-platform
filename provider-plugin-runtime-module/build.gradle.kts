plugins {
    id("java-library")
}

group = "com.example.platform"
version = "1.0.0"

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
    api(project(":extension-module"))
    api(project(":worker-fabric-module"))
    api(project(":media-execution-plan-module"))
    api(project(":sandbox-isolation-module"))
    api("org.pf4j:pf4j:3.15.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
