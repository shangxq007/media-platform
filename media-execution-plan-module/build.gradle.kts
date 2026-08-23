plugins {
    id("java-library")
    id("jacoco")
}

group = "com.example.platform"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Java toolchain must match project standard (Java 25 via foojay convention)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// Retain parameter names for Spring MVC binding compatibility
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

dependencies {
    // Media Execution Plan Domain depends only on the Artifact Domain and StorageProvider SPI,
    // both of which live in render-module and artifact-module. No dependency on OpenDAL adapter,
    // cloud SDKs, bucket names, object keys, signed URLs, Kubernetes, or FFmpeg.
    implementation(project(":artifact-module"))
    implementation(project(":render-module"))
    implementation(project(":extension-module"))
    implementation(project(":audio-module"))
    implementation(project(":color-image-module"))
    implementation(project(":font-text-module"))
    implementation(project(":platform-algorithms:graph"))

    // JSON for canonical serialization helpers
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.slf4j:slf4j-simple")
    testImplementation(testFixtures(project(":render-module")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Xmx2g", "-XX:+HeapDumpOnOutOfMemoryError", "--enable-native-access=ALL-UNNAMED")
    systemProperty("aws.ec2.metadata.disabled", "true")
    systemProperty("http.nonProxyHosts", "localhost|127.0.0.1")
    systemProperty("http.proxyHost", "")
    systemProperty("https.proxyHost", "")
    environment("AWS_ACCESS_KEY_ID", "test-access-key")
    environment("AWS_SECRET_ACCESS_KEY", "test-secret-key")
    environment("AWS_REGION", "us-east-1")
}

tasks.withType<JacocoReport> {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}