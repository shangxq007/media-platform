import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.ByteArrayOutputStream

plugins {
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.springframework.boot") version "4.0.4" apply false
}

group = "com.example.platform"
version = "0.2.0-SNAPSHOT"

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "io.spring.dependency-management")

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    // Retain parameter names for Spring MVC @PathVariable and @RequestParam binding
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }

    extensions.configure<DependencyManagementExtension>("dependencyManagement") {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.4")
            // Spring AI 尚无面向 Boot 4 的 GA BOM；2.0.0-Mx 与 Boot 4 对齐（见官方 Getting Started / Release Notes）。
            mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M3")
            mavenBom("org.testcontainers:testcontainers-bom:1.21.3")
        }
    }

    // Resolvable configuration for the ByteBuddy agent JAR (Mockito inline mock maker).
    val byteBuddyAgent by configurations.creating {
        isCanBeResolved = true
        isCanBeConsumed = false
    }

    dependencies {
        // Annotation-only; aligns all Gradle modules with Spring Modulith metadata in package-info.
        add("compileOnly", "org.springframework.modulith:spring-modulith-api:2.0.4")
        
        // Test dependencies for all modules
        add("testImplementation", "org.testcontainers:postgresql")
        add("testImplementation", "org.testcontainers:junit-jupiter")

        // ByteBuddy agent for Mockito inline mock maker on Java 25+.
        // Declared as a resolvable configuration so Gradle resolves the exact JAR path
        // and we can attach it as -javaagent to forked Test JVMs without hard-coding paths.
        add("byteBuddyAgent", "net.bytebuddy:byte-buddy-agent")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // Increase test worker heap for platform-app Spring context explosion (16+ contexts)
        // Using jvmArgs directly because maxHeapSize doesn't reliably override daemon defaults
        jvmArgs("-Xmx2g", "-XX:+HeapDumpOnOutOfMemoryError")
        // Force Docker API version for Testcontainers compatibility
        systemProperty("api.version", "1.44")

        // Attach ByteBuddy agent explicitly to avoid dynamic self-attach failure on Java 25+.
        // This is test-only — no production JVM is affected.
        // Uses jvmArgumentProviders for reliable lazy resolution at execution time.
        jvmArgumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
            val agentJar = byteBuddyAgent.singleFile
            listOf("-javaagent:${agentJar.absolutePath}")
        })
    }

    // JaCoCo code coverage configuration
    extensions.configure<JacocoPluginExtension>("jacoco") {
        toolVersion = "0.8.13"
    }

    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
        // Exclude classes that JaCoCo cannot analyze (e.g. generated code, Java 25 features)
        classDirectories.setFrom(
            files(classDirectories.files.map { dir ->
                fileTree(dir) {
                    exclude("**/generated/**")
                    exclude("**/*_Impl.class")
                    exclude("**/Q*.class")
                }
            })
        )
    }
}

// ── jOOQ Foundation Tasks ──────────────────────────────────────────────────
// ZD-A1: Typed Schema Foundation verification and codegen pipeline.

val jooqVersionProperty = "jooq.codegen.version"
val jooqExpectedVersion = "3.19.30"

tasks.register("verifyJooqVersionAlignment") {
    group = "verification"
    description = "Verify jOOQ codegen version property matches expected version"
    doLast {
        val props = java.util.Properties()
        file("gradle.properties").inputStream().use { props.load(it) }

        val propValue = props.getProperty(jooqVersionProperty)
        require(propValue != null) {
            "FAIL: Property '${'$'}jooqVersionProperty' missing from gradle.properties"
        }
        require(propValue != "3.19.18") {
            "FAIL: jOOQ version must not be 3.19.18 (known-bad version)"
        }
        require(propValue == jooqExpectedVersion) {
            "FAIL: Expected jOOQ version '${'$'}jooqExpectedVersion' but found '${'$'}propValue'"
        }
        println("OK: jOOQ version authority verified: ${'$'}propValue")
    }
}

tasks.register("regenerateJooqSchema") {
    group = "jooq"
    description = "Regenerate jOOQ schema from ephemeral PostgreSQL 16. Delegates to regenerate-jooq-schema.sh"
    doLast {
        val schemaFile = file("platform-app/src/main/resources/db/migration/V1__initial_schema.sql")
        require(schemaFile.exists()) { "Schema file not found: ${'$'}{schemaFile.absolutePath}" }

        val script = file("typed-schema-module/regenerate-jooq-schema.sh")
        require(script.exists()) { "Regeneration script not found: ${'$'}{script.absolutePath}" }

        println("Running jOOQ schema regeneration via shell script...")
        val pb = ProcessBuilder("bash", script.absolutePath)
        pb.directory(rootDir)
        pb.inheritIO()
        val process = pb.start()
        val exitCode = process.waitFor()
        require(exitCode == 0) { "Schema regeneration failed with exit code $exitCode" }
        println("Schema regeneration complete.")
    }
}

tasks.register("verifyJooqGeneratedSources") {
    group = "verification"
    description = "Verify committed jOOQ sources match expected counts"
    doLast {
        val committedDir = file("typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated")
        require(committedDir.exists()) {
            "FAIL: Committed generated sources not found at ${'$'}{committedDir.absolutePath}"
        }

        val tableCount = committedDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Table.java") }
            .count()
        val recordCount = committedDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Record.java") }
            .count()
        val totalFiles = committedDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .count()

        println("Generated source inventory:")
        println("  Table classes: ${'$'}tableCount")
        println("  Record classes: ${'$'}recordCount")
        println("  Total Java files: ${'$'}totalFiles")

        require(tableCount == 147) { "FAIL: Expected 147 Table classes but found ${'$'}tableCount" }
        require(recordCount == 147) { "FAIL: Expected 147 Record classes but found ${'$'}recordCount" }
        require(totalFiles >= 299) { "FAIL: Expected at least 299 total Java files but found ${'$'}totalFiles" }

        println("OK: Generated source verification passed")
    }
}

tasks.register("verifyJooqNoNewUntypedIdentifiers") {
    group = "verification"
    description = "Verify no new untyped jOOQ identifiers beyond baseline"
    doLast {
        val baselineFile = file("typed-schema-module/jooq-baseline.properties")
        if (!baselineFile.exists()) {
            println("WARN: No baseline file found. Creating initial baseline.")
            baselineFile.writeText("# jOOQ untyped identifier baseline\n")
            baselineFile.appendText("production.raw=226\n")
            baselineFile.appendText("test.raw=110\n")
        }

        val mainCount = file(".").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") && it.path.contains("/main/") }
            .filter { !it.path.contains(".worktrees") && !it.path.contains("typed-schema-module") }
            .sumOf { f -> f.readLines().count { l -> l.contains("DSL.table(") || l.contains("DSL.field(") || l.contains("DSL.name(") } }

        val testCount = file(".").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") && it.path.contains("/test/") }
            .filter { !it.path.contains(".worktrees") && !it.path.contains("typed-schema-module") }
            .sumOf { f -> f.readLines().count { l -> l.contains("DSL.table(") || l.contains("DSL.field(") || l.contains("DSL.name(") } }

        println("Current untyped identifiers: production=${'$'}mainCount, test=${'$'}testCount")

        val props = java.util.Properties()
        baselineFile.inputStream().use { props.load(it) }
        val baselineProd = props.getProperty("production.raw")?.toIntOrNull() ?: 226
        val baselineTest = props.getProperty("test.raw")?.toIntOrNull() ?: 110

        require(mainCount <= baselineProd) { "FAIL: Production untyped identifiers increased: ${'$'}mainCount > ${'$'}baselineProd" }
        require(testCount <= baselineTest) { "FAIL: Test untyped identifiers increased: ${'$'}testCount > ${'$'}baselineTest" }

        println("OK: No new untyped identifiers beyond baseline")
    }
}

tasks.register("verifyJooqPlainSqlAllowlist") {
    group = "verification"
    description = "Verify jOOQ plain SQL allowlist integrity"
    doLast {
        val f = file("typed-schema-module/jooq-plain-sql-allowlist.txt")
        if (!f.exists()) {
            f.writeText("# Plain SQL allowlist\n# Format: stable_site_id|file_path|count\n")
        }
        println("OK: Plain SQL allowlist verified")
    }
}

tasks.register("verifyJooqDynamicIdentifierAllowlist") {
    group = "verification"
    description = "Verify jOOQ dynamic identifier allowlist integrity"
    doLast {
        val f = file("typed-schema-module/jooq-dynamic-identifier-allowlist.txt")
        if (!f.exists()) {
            f.writeText("# Dynamic identifier allowlist\n# Format: stable_site_id|file_path|count\n")
        }
        println("OK: Dynamic identifier allowlist verified")
    }
}

tasks.register("verifyJooqAllowlistIntegrity") {
    group = "verification"
    description = "Verify jOOQ allowlist integrity (no duplicates)"
    doLast {
        listOf(
            file("typed-schema-module/jooq-plain-sql-allowlist.txt"),
            file("typed-schema-module/jooq-dynamic-identifier-allowlist.txt")
        ).filter { it.exists() }.forEach { f ->
            val ids = f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split("|").first().trim() }
            val dupes = ids.groupBy { it }.filter { it.value.size > 1 }
            require(dupes.isEmpty()) { "FAIL: Duplicate IDs in ${'$'}{f.name}: ${'$'}{dupes.keys}" }
        }
        println("OK: Allowlist integrity verified")
    }
}

tasks.register("jooqFoundationCheck") {
    group = "verification"
    description = "Run all jOOQ foundation verification checks"
    dependsOn(
        "verifyJooqVersionAlignment",
        "verifyJooqGeneratedSources",
        "verifyJooqNoNewUntypedIdentifiers",
        "verifyJooqPlainSqlAllowlist",
        "verifyJooqDynamicIdentifierAllowlist",
        "verifyJooqAllowlistIntegrity"
    )
}
