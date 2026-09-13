plugins { id("java-library") }

dependencies {
    implementation(project(":outbox-event-module"))
    implementation(project(":artifact-module"))
    implementation(project(":render-module")) // defining-domain lifecycle contract
    implementation(project(":identity-access-module")) // published authorization contract
    implementation(project(":typed-schema-module"))
    api(project(":shared-kernel"))
    api(project(":storage-module"))
    implementation(project(":secrets-config-module"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.github.mwiede:jsch:0.2.21")
    implementation("com.hierynomus:smbj:0.13.0")
    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.flywaydb:flyway-core")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testImplementation(testFixtures(project(":shared-kernel")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
