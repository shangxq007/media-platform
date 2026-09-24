plugins { id("java-library") }

dependencies {
    api(project(":shared-kernel"))
    api(project(":extension-module"))
    implementation(project(":identity-access-module"))
    implementation(project(":artifact-module"))
    implementation(project(":storage-module"))
    implementation(project(":media-module"))
    implementation(project(":worker-fabric-module"))
    implementation(project(":media-execution-plan-module"))
    implementation(project(":entitlement-module"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.43")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":shared-kernel")))
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Package the canonical platform grammar; no provider-owned syntax is loaded.
tasks.processResources {
    from(rootProject.file("contracts/composition/version-range-v1.json")) { into("composition") }
}
tasks.processTestResources {
    from(rootProject.file("contracts/composition/version-range-cases.json")) { into("composition") }
}
