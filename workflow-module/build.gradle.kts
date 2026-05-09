plugins { id("java-library") }

dependencies {
    api(project(":policy-governance-module"))
    api(project(":render-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("io.temporal:temporal-sdk:1.33.0")
    compileOnly("io.temporal:temporal-spring-boot-autoconfigure:1.33.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}