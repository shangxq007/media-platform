plugins { id("java-library") }

dependencies {
    api(project(":shared-kernel"))
    api(project(":extension-module"))
    implementation(project(":identity-access-module"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":shared-kernel")))
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
