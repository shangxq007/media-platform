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
    implementation(project(":media-execution-plan-module"))
    implementation(project(":worker-fabric-module"))
    // ABI companions referenced by canonical execution-plan signatures.
    compileOnly(project(":render-module"))
    compileOnly(project(":extension-module"))

    testImplementation(project(":render-module"))
    testImplementation(project(":extension-module"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
