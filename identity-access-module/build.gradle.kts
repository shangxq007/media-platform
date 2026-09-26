plugins { id("java-library") }

dependencies {
    implementation(project(":audit-contract-module")) // owner-published audit ports
    implementation(project(":typed-schema-module"))
    implementation(project(":observability-module")) // TraceKeys rehomed to observability (K2)
    api(project(":shared-kernel"))
    api(project(":entitlement-module"))
    api(project(":artifact-module"))
    api(project(":storage-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
    // Direct Jackson 2.x usage (DTO/ObjectMapper/JavaTimeModule across
    // identity API and authn); previously obtained transitively via
    // shared-kernel's removed exports — K2-03
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":shared-kernel")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// AUTH-INVENTORY-FIX-002 (F-3): AuthPermissionVocabularyConsistencyTest parses the production
// permission seed (this module's main sources, already a test input) and the Flyway permission rows
// under platform-app/src/main/resources/db/migration — cross-module files that are not on this
// module's classpath. Declaring them as test inputs keeps a migration-only edit from being reported
// UP-TO-DATE while skipping the consistency check.
tasks.named<org.gradle.api.tasks.testing.Test>("test") {
    inputs.dir(rootProject.file("platform-app/src/main/resources/db/migration"))
        .withPropertyName("permissionMigrationSeeds")
}
