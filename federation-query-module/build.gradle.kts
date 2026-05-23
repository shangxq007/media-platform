plugins { id("java-library") }

dependencies {
    api(project(":shared-kernel"))
    api(project(":entitlement-module"))
    api(project(":identity-access-module"))
    api(project(":policy-governance-module"))
    api(project(":billing-module"))
    api(project(":extension-module"))
    api(project(":render-module"))
    api(project(":prompt-module"))
    api(project(":user-analytics-module"))
    api(project(":audit-compliance-module"))
    api(project(":ai-module"))
    api(project(":datasource-module"))

    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-graphql")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-jdbc")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
