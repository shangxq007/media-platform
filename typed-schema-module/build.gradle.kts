plugins {
    id("java-library")
}

// This module owns generated jOOQ schema classes only.
// No Spring, no JDBC, no PostgreSQL driver dependency at compile or runtime.
// Consumers depend on this module for type-safe table/record references.

// Q1-MA1: the Modulith named-interface marker (@NamedInterface("jooq-tables") on the
// generated tables package) must NOT live inside the jOOQ generation target
// (src/main/java) — GenerationTool deletes non-generated files there ("Removing
// excess files"). It is therefore declared from a separate, generator-immune source
// root that compiles into the same package (JOOQ_REGENERATION_MUST_PRESERVE_TYPED_SCHEMA_NAMED_INTERFACE_AUTHORITY_V1).
sourceSets {
    main {
        java {
            srcDir("src/main/modulith/java")
        }
    }
}

// Opt out of Spring dependency management — this module is pure jOOQ.
configurations.all {
    // Remove Spring BOM constraints; we manage jOOQ version directly.
}

dependencies {
    // jOOQ core — the sole runtime dependency
    api("org.jooq:jooq:${property("jooq.codegen.version")}")

    // JavaParser — for AST-based untyped call detection (compile/test only)
    implementation("com.github.javaparser:javaparser-core:3.25.10")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Test> {
    systemProperty("project.root.dir", rootProject.projectDir.absolutePath)
}
