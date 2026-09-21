plugins { id("java-library") }

dependencies {
    implementation(project(":audit-contract-module")) // owner-published audit ports
    api(project(":shared-kernel"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
