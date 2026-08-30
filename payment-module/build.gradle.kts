plugins { id("java-library") }

dependencies {
    implementation(project(":commerce-module")) // CheckoutPaymentPort contract rehomed to commerce (K2)
    api(project(":shared-kernel"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
    // Direct Jackson 2.x usage (HyperswitchHttpPaymentProvider, WebhookPayloadSupport);
    // previously obtained transitively via shared-kernel's removed export — K2-03
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":shared-kernel")))
    testImplementation("org.postgresql:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
