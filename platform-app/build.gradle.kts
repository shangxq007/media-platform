plugins { id("org.springframework.boot") }

dependencies {
    implementation(project(":shared-kernel"))
    testImplementation(testFixtures(project(":shared-kernel")))
    implementation(project(":render-module"))
    implementation(project(":notification-module"))
    implementation(project(":ai-module"))
    implementation(project(":config-module"))
    implementation(project(":workflow-module"))
    implementation(project(":storage-module"))
    implementation(project(":delivery-module"))
    implementation(project(":prompt-module"))
    implementation(project(":cloud-resource-module"))
    implementation(project(":secrets-config-module"))
    implementation(project(":extension-module"))
    implementation(project(":datasource-module"))
    implementation(project(":observability-module"))
    implementation(project(":outbox-event-module"))
    implementation(project(":audit-compliance-module"))
    implementation(project(":scheduler-module"))
    implementation(project(":identity-access-module"))
    implementation(project(":quota-billing-module"))
    implementation(project(":commerce-module"))
    implementation(project(":payment-module"))
    implementation(project(":billing-module"))
    implementation(project(":entitlement-module"))
    implementation(project(":policy-governance-module"))
    implementation(project(":artifact-catalog-module"))
    implementation(project(":sandbox-runtime-module"))
    implementation(project(":federation-query-module"))
    implementation(project(":user-analytics-module"))
    implementation(project(":social-publish-module"))
    implementation(project(":compatibility-migration-module"))

    implementation("org.springframework.boot:spring-boot-starter-graphql")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    // Flyway 10+ community DB support (required when using PostgreSQL at runtime, e.g. Docker / prod).
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-web")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("org.springframework.modulith:spring-modulith-starter-core:2.0.4")
    runtimeOnly("org.springframework.modulith:spring-modulith-starter-insight:2.0.4")
    implementation("io.temporal:temporal-spring-boot-starter:1.33.0")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("com.yomahub:liteflow-spring-boot-starter:2.15.3.2")
    implementation("org.pf4j:pf4j:3.15.0")

    testImplementation("org.springframework.modulith:spring-modulith-starter-test:2.0.4")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
    testImplementation("org.testcontainers:postgresql:1.20.6")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    args("--spring.profiles.active=local-preview")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("render-integration")
    }
}

tasks.register<Test>("renderIntegrationTest") {
    description = "Runs render pipeline integration tests"
    group = "verification"
    useJUnitPlatform {
        includeTags("render-integration")
    }
    shouldRunAfter(tasks.test)
}
