plugins {
    id("java-library")
    id("java-test-fixtures")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Expose test fixtures
    testFixturesImplementation("org.testcontainers:postgresql")
    testFixturesImplementation("org.testcontainers:junit-jupiter")
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
}