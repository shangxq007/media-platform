plugins { id("java-library") }

// Usage-owned public contracts only: no persistence, Spring service or Outbox dependency.
dependencies {
    api(project(":shared-kernel")) // CanonicalActor attribution snapshot, not accounting authority
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
