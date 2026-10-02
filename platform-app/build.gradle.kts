plugins { id("org.springframework.boot") }

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    mainClass.set("com.example.platform.PlatformApplication")
}

// COVER-THUMBNAIL-REBUILD-001 (action 3): one runtime-grouped ffmpeg worker boot jar replaces the
// retired cover-image and thumbnail per-capability worker boot jars.
val ffmpegWorkerBootJar = tasks.register<org.springframework.boot.gradle.tasks.bundling.BootJar>("ffmpegWorkerBootJar") {
    group = "build"
    description = "Builds the worker-only executable with no API HTTP entry point."
    archiveFileName.set("platform-ffmpeg-worker.jar")
    mainClass.set("com.example.platform.runtime.PlatformFfmpegWorkerApplication")
    targetJavaVersion.set(org.gradle.api.JavaVersion.VERSION_25)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    classpath(sourceSets.main.get().runtimeClasspath)
    dependsOn(tasks.named("classes"))
}

dependencies {
    implementation(project(":audit-contract-module")) // owner-published audit ports
    implementation(project(":marketplace-module"))
    implementation(project(":sandbox-isolation-module"))
    implementation(project(":typed-schema-module"))
    implementation("org.apache.tika:tika-core:2.9.2")  // Tika experimental (disabled by default)
    implementation(project(":shared-kernel"))
    testImplementation(testFixtures(project(":shared-kernel")))
    implementation(project(":render-module"))
    implementation(project(":timeline-module"))
    implementation(project(":media-module"))
    testImplementation("io.temporal:temporal-testing:1.33.0")
    testImplementation(project(":audio-module"))
    implementation(project(":operation-module"))
    implementation(project(":font-text-module"))
    implementation(project(":notification-module"))
    implementation(project(":ai-module"))
    implementation(project(":config-module"))
    implementation(project(":workflow-module"))
    implementation(project(":storage-module"))
    implementation(project(":delivery-module"))
    implementation(project(":prompt-module"))
    implementation(project(":cloud-resource-module"))
    implementation(project(":secrets-config-module"))
    implementation(project(":extension-module"))
    implementation(project(":datasource-module"))
    implementation(project(":observability-module"))
    implementation(project(":outbox-event-module"))
    implementation(project(":audit-compliance-module"))
    implementation(project(":scheduler-module"))
    implementation(project(":identity-access-module"))
    implementation(project(":commerce-module"))
    implementation(project(":payment-module"))
    implementation(project(":billing-module"))
    implementation(project(":entitlement-module"))
    implementation(project(":policy-governance-module"))
    implementation(project(":artifact-module"))
    implementation(project(":extension-module"))
    implementation(project(":federation-query-module"))
    implementation(project(":user-analytics-module"))
    implementation(project(":social-publish-module"))
    implementation(project(":worker-fabric-module"))
    implementation(project(":provider-plugin-runtime-module"))
    implementation(project(":composition-module"))
    implementation(project(":media-capability-contracts"))

    implementation("org.springframework.boot:spring-boot-starter-graphql")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    // Flyway 10+ community DB support (required when using PostgreSQL at runtime, e.g. Docker / prod).
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-web")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("org.springframework.modulith:spring-modulith-starter-core:2.0.4")
    implementation("io.temporal:temporal-spring-boot-starter:1.33.0")
    implementation("com.yomahub:liteflow-spring-boot-starter:2.15.3.2")
    implementation("org.pf4j:pf4j:3.15.0")

    testImplementation("org.springframework.modulith:spring-modulith-starter-test:2.0.4")
    testImplementation("org.springframework.modulith:spring-modulith-docs:2.0.4")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Version authority is spring-boot-dependencies:4.0.4 (testcontainers 2.0.4) — no inline pin.
    // Testcontainers 2.x renamed these artifacts (old coordinates do not exist at 2.0.4).
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("render-integration")
    }
    // AUTH-INVENTORY-FIX-002 (F-3): EffectiveAccessFactorVocabularyConsistencyTest reads the client
    // effective-access factor contract (frontend/src/foundation/effectiveAccess.tsx), a cross-tree
    // file that is not on the Java classpath. Declaring it as a task input makes the naming guard
    // re-run when the frontend contract changes, instead of being reported UP-TO-DATE.
    inputs.file(rootProject.file("frontend/src/foundation/effectiveAccess.tsx"))
        .withPropertyName("effectiveAccessFactorContract")
}

tasks.register<Test>("phase17SandboxConformanceTest") {
    description = "Runs the exact authoritative Phase 17 FFprobe sandbox conformance methods."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        excludeTags("render-integration")
    }
    filter {
        includeTestsMatching(
            "com.example.platform.ingest.preflight.ffprobe.FFprobeMediaMetadataProviderTest.testValidVideoIfFFprobeAvailable")
        includeTestsMatching(
            "com.example.platform.ingest.preflight.IngestMetadataMergerTest.testFfprobeForVideo")
        isFailOnNoMatchingTests = true
    }
}

tasks.register<Test>("renderIntegrationTest") {
    description = "Runs render pipeline integration tests"
    group = "verification"
    useJUnitPlatform {
        includeTags("render-integration")
    }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    shouldRunAfter(tasks.test)
}
