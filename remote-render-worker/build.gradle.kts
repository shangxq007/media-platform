plugins {
    id("java-library")
    id("org.springframework.boot")
}

dependencies {
    api(project(":shared-kernel"))
    api(project(":render-module"))
    api(project(":storage-module"))
    api(project(":ai-module"))

    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-actuator")

    api("org.bytedeco:javacv-platform:1.5.9")
    api("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
