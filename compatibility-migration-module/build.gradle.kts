plugins { id("java-library") }

dependencies {
    api(project(":shared-kernel"))
    api(project(":policy-governance-module"))
    api(project(":extension-module"))
    api(project(":audit-compliance-module"))
    api(project(":outbox-event-module"))
    api(project(":scheduler-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("com.yomahub:liteflow-spring-boot-starter:2.15.3.2")
    api("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
