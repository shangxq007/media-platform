plugins { id("java-library") }

dependencies {
    implementation(project(":shared-kernel"))
    implementation(project(":typed-schema-module"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework:spring-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":shared-kernel")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
