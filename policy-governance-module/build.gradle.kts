plugins { id("java-library") }

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("dev.openfeature:sdk:1.20.2")
    api("dev.openfeature.contrib.providers:unleash:0.1.3-alpha")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
