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
    // GCR-2: artifact-module depends on storage-module contracts (ContentDigest was moved
    // to shared-kernel; storage contracts StorageObjectId/StorageReplicaId/StorageProviderId
    // remain storage data-plane). The stale render-module dependency is REMOVED — artifact
    // main code has zero render imports (fixes the #14 dependency-cycle premise).
    implementation(project(":storage-module"))

    // PMPR-A1R1: artifact catalog absorbed — shared-kernel API + micrometer for catalog services/metrics
    api(project(":shared-kernel"))
    implementation("io.micrometer:micrometer-core")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // jOOQ generated sources for typed persistence access
    implementation(project(":typed-schema-module"))

    // Spring JDBC / jOOQ for repository implementations
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    // JSON for canonical serialization helpers
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.slf4j:slf4j-simple")
    testImplementation(testFixtures(project(":render-module")))
    testImplementation(testFixtures(project(":shared-kernel")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.postgresql:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    runtimeOnly("org.postgresql:postgresql")
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
