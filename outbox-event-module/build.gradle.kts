plugins { id("java-library") }

dependencies {
    implementation(project(":typed-schema-module"))
    implementation(project(":notification-module")) // NotificationEventPublisher rehomed to notification (K2)
    api(project(":shared-kernel"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
    api("io.micrometer:micrometer-registry-prometheus")
    // Direct Jackson 2.x usage (OutboxEventDispatcher parses payloads with
    // ObjectMapper; shared-kernel no longer exports jackson-databind — K2-03)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":shared-kernel")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
