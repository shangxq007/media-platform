plugins { id("java-library") }

dependencies {
    implementation(project(":typed-schema-module"))
    implementation(project(":billing-module")) // cost ports rehomed to billing (K2)
    api(project(":shared-kernel"))
    api(project(":policy-governance-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":shared-kernel")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
