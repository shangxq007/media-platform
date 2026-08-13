dependencies {
    implementation(project(":shared-kernel"))
    implementation(project(":billing-module"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Direct Jackson 2.x usage (HttpSandboxWorkerAdapter); previously obtained
    // transitively via shared-kernel's removed databind export — K2-03
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.apache.commons:commons-exec:1.6.0")
    implementation("org.pf4j:pf4j:3.15.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
