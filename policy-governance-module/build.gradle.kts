plugins { id("java-library") }

dependencies {
    api(project(":shared-kernel"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    api("dev.openfeature:sdk:1.20.2")
    api("dev.openfeature.contrib.providers:unleash:0.1.3-alpha")
    // Direct jakarta.validation usage (FeatureFlagController); previously obtained
    // transitively via shared-kernel's removed validation export — K2-03
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
