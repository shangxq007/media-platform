plugins {
    id("java-library")
    id("java-test-fixtures")
}

dependencies {
    // K2-03: transitive framework surface reduction. `api` now exposes only what
    // public contracts require: jackson-core (TypeReference appears in the
    // Jsons public signature). Jackson databind/JSR-310 and Spring are
    // implementation details of the serialization boundary / retained
    // ErrorCodeRegistry composition — consumers with direct framework usage
    // declare their own legitimate dependencies.
    implementation("org.springframework.boot:spring-boot")
    implementation("org.slf4j:slf4j-api")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("com.fasterxml.jackson.core:jackson-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-web")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Expose test fixtures
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
    testFixturesImplementation("org.postgresql:postgresql")
    testFixturesImplementation("com.zaxxer:HikariCP")
    testFixturesImplementation("org.junit.platform:junit-platform-launcher")
    testFixturesImplementation("org.jooq:jooq")
    testImplementation("org.jooq:jooq")
    testImplementation("com.zaxxer:HikariCP")
}
