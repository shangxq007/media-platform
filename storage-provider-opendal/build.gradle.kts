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
    // Platform storage provider API — uses render-module which contains StorageProvider SPI
    implementation(project(":render-module"))
    implementation(project(":storage-module"))

    // Apache OpenDAL — fixed version, no SNAPSHOT, no dynamic versions
    // Uses 'implementation' (not 'api') to prevent transitive leakage to consumers
    implementation("org.apache.opendal:opendal-java:0.46.4")
    // Platform-specific native library for Linux x86_64
    implementation("org.apache.opendal:opendal-java:0.46.4:linux-x86_64")

    // Logging (SLFJ4 — provided by render-module transitively)
    compileOnly("org.slf4j:slf4j-api")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.slf4j:slf4j-simple")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Xmx2g", "-XX:+HeapDumpOnOutOfMemoryError", "--enable-native-access=ALL-UNNAMED")
    // Disable AWS EC2 metadata service and provide test credentials via environment
    systemProperty("aws.ec2.metadata.disabled", "true")
    systemProperty("http.nonProxyHosts", "localhost|127.0.0.1")
    systemProperty("http.proxyHost", "")
    systemProperty("https.proxyHost", "")
    environment("AWS_ACCESS_KEY_ID", "test-access-key")
    environment("AWS_SECRET_ACCESS_KEY", "test-secret-key")
    environment("AWS_EC2_METADATA_DISABLED", "true")
    environment("AWS_REGION", "us-east-1")
    environment("NO_PROXY", "localhost,127.0.0.1")
}

tasks.withType<JacocoReport> {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
