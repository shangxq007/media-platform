plugins {
    id("java-library")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    environment("HOME", "/phase17-ambient-home-not-mounted")
    environment("AWS_SECRET_ACCESS_KEY", "phase17-ambient-aws-secret")
}

tasks.register<Test>("phase17SandboxConformanceTest") {
    description = "Runs the exact authoritative Phase 17 sandbox conformance methods."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "com.example.platform.sandbox.BubblewrapSandboxProcessLauncherIntegrationTest.real_bubblewrap_enforces_the_advertised_host_binary_boundaries")
        includeTestsMatching(
            "com.example.platform.sandbox.ContainerSandboxProcessLauncherIntegrationTest.rootless_container_mechanically_enforces_the_advertised_boundaries")
        isFailOnNoMatchingTests = true
    }
}
