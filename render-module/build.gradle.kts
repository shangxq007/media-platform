plugins {
    id("java-library")
    id("java-test-fixtures")
}

dependencies {
    api(project(":shared-kernel"))
    testImplementation(testFixtures(project(":shared-kernel")))
    api(project(":ai-module"))
    api(project(":storage-module"))
    api(project(":extension-module"))
    api(project(":entitlement-module"))
    api(project(":billing-module"))
    api(project(":quota-billing-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("com.yomahub:liteflow-spring-boot-starter:2.15.3.2")
    api("org.bytedeco:javacv-platform:1.5.9")
    api("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
