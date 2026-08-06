plugins { id("java-library") }

dependencies {
    api(project(":policy-governance-module"))
    api(project(":render-module"))
    implementation(project(":delivery-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("io.temporal:temporal-sdk:1.33.0")
    compileOnly("io.temporal:temporal-spring-boot-autoconfigure:1.33.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Frozen conditional path (TEPHV1 CONTRACT_V1, conditional trigger proven:
    // RED-1 compile failure demonstrated missing temporal-testing; authentic
    // TestWorkflowEnvironment tests required for retry/timeout/cancellation/
    // heartbeat semantics). Test-only, version aligned with temporal-sdk 1.33.0.
    testImplementation("io.temporal:temporal-testing:1.33.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}