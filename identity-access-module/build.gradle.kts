plugins { id("java-library") }

dependencies {
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
