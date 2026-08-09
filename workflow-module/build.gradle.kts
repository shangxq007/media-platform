plugins { id("java-library") }

dependencies {
    api(project(":policy-governance-module"))
    api(project(":render-module"))
    implementation(project(":delivery-module"))
    // UWEV1-FV1: effect execution boundary via extension::runtime (UWE-ADR-025)
    implementation(project(":extension-module"))
    // EUMF canonical types + durable terminal transitions (ArtifactRef lives in
    // shared-kernel; artifact-module is intentionally NOT depended on — its
    // render-bound ContentDigest debt must not enter the workflow module graph).
    implementation(project(":billing-module"))
    implementation(project(":outbox-event-module"))
    // UWDV1-V2-PIC: activated conditional path (USER_WORKFLOW_DEFINITION_V1_CONTRACT_V2
    // conditional-path-allowlist.tsv): W2 graph validation reuses the deterministic
    // platform-algorithms/graph kernel (G-008/G-009/G-010).
    api(project(":platform-algorithms:graph"))
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
    // UWDV1-V2-PIC: test infrastructure required by database-test-contract.txt
    // (PostgresTestContainerSupport lives in shared-kernel testFixtures; the
    // frozen JDBC integration test extends it). Documented in implementation-summary.txt.
    testImplementation(testFixtures(project(":shared-kernel")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}