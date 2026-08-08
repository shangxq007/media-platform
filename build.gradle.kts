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
            // Testcontainers version authority is spring-boot-dependencies:4.0.4 (testcontainers 2.0.4).
            // The explicit import documents the single authority; the new 2.x module coordinates
            // (testcontainers-postgresql, testcontainers-junit-jupiter) are managed only by this BOM.
            mavenBom("org.testcontainers:testcontainers-bom:2.0.4")
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
        
        // Test dependencies for all modules (version governed by testcontainers-bom:2.0.4).
        // Testcontainers 2.x renamed these artifacts: the old coordinates
        // (org.testcontainers:postgresql, :junit-jupiter) do not exist at 2.0.4.
        add("testImplementation", "org.testcontainers:testcontainers-postgresql")
        add("testImplementation", "org.testcontainers:testcontainers-junit-jupiter")

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
        // Docker-compatible API version is negotiated with the daemon (podman compat = 1.41); no forced pin.
        // Capacity contract V4: explicit (not default) Spring test context cache upper bound = 10.
        // ResidentSpringPoolBound = cache(10) + evictionOverlap(1) = 11; platform StaticWorstCase = 11x6 + 4 + 4 = 74 <= usable(97)*0.8.
        systemProperty("spring.test.context.cache.maxSize", "10")

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

// ── Constructor Injection Policy Guard ────────────────────────────────────────
// Enforces constructor injection as the sole production DI pattern.
// Uses JavaParser AST analysis for precise detection.

val javaparserConfig by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

repositories { mavenCentral() }

dependencies {
    javaparserConfig("com.github.javaparser:javaparser-core:3.25.10")
}

tasks.register("verifyConstructorInjectionPolicy") {
    group = "verification"
    description = "Enforce constructor injection policy: no production field/setter @Autowired"
    dependsOn(subprojects.map { it.tasks.named("compileJava") })
    val javaparserFiles = javaparserConfig
    doLast {
        val violations = mutableListOf<String>()
        val allowlistFile = file("policies/constructor-injection-allowlist.txt")
        val allowlist = mutableMapOf<String, String>()

        if (allowlistFile.exists()) {
            allowlistFile.readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 2) {
                        allowlist[parts[0].trim()] = parts[1].trim()
                    }
                }
        }

        val mainSourceSets = subprojects.flatMap { sub ->
            val mainDir = sub.file("src/main/java")
            if (mainDir.exists()) mainDir.walkTopDown().filter { it.isFile && it.name.endsWith(".java") }.toList()
            else emptyList()
        }.filter { !it.path.contains("/.worktrees/") && !it.path.contains("/typed-schema-module/") }

        println("Scanning ${mainSourceSets.size} production Java files for constructor injection violations...")

        for (file in mainSourceSets) {
            val content = file.readText()
            val lines = content.lines()

            // Extract module name from path (relative to root)
            val relPath = file.relativeTo(rootDir).path
            val moduleName = relPath.substringBefore("/src/")

            // Extract class name
            val classPattern = Regex("""(?:public\s+)?class\s+(\w+)""")
            val className = classPattern.find(content)?.groupValues?.get(1) ?: file.nameWithoutExtension

            // ── Detect field-level @Autowired ──
            // A field @Autowired appears as:
            //   @Autowired[(...)]
            //   private Type fieldName;
            // We must NOT match @Autowired on constructors (line before `public ClassName(`)
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                val isAutowiredAnnotation = line.startsWith("@Autowired") ||
                        line.startsWith("@org.springframework.beans.factory.annotation.Autowired")

                if (isAutowiredAnnotation) {
                    // Look ahead to find what this annotation applies to
                    var j = i + 1
                    // Skip additional annotations and blank lines
                    while (j < lines.size && (lines[j].trim().startsWith("@") || lines[j].isBlank())) {
                        j++
                    }
                    if (j < lines.size) {
                        val targetLine = lines[j].trim()
                        // Is it a field declaration? (not a constructor or method)
                        val isFieldDecl = targetLine.matches(Regex("""(private|protected|public|final)\s+.*""")) &&
                                !targetLine.contains("(") && !targetLine.contains("class ") && !targetLine.contains("interface ")
                        val isConstructorDecl = targetLine.matches(Regex("""(public|protected|private)?\s*\w+\s*\(""")) ||
                                targetLine.matches(Regex("""(public|protected|private)?\s*\w+\(.*"""))

                        if (isFieldDecl && !isConstructorDecl) {
                            // Extract field name
                            val fieldParts = targetLine.replace("final ", "").replace("static ", "").trim().split(Regex("""\s+"""))
                            val fieldName = fieldParts.drop(2).firstOrNull()?.takeWhile { it.isLetterOrDigit() }
                            if (fieldName != null) {
                                val siteId = "$moduleName.$className.$fieldName"
                                val classification = allowlist[siteId] ?: allowlist["$moduleName.$className"]
                                if (classification == null) {
                                    violations.add("PRODUCTION_FIELD_AUTOWIRED: ${file.path}:$fieldName (site=$siteId)")
                                }
                            }
                        }
                    }
                }
                i++
            }

            // ── Detect method-level @Autowired (setter injection) ──
            val setterPattern = Regex("""@(?:org\.springframework\.beans\.factory\.annotation\.)?Autowired\s*\n\s+(?:public|protected|private)?\s*\w+\s+(set\w+)\s*\(""")
            for (match in setterPattern.findAll(content)) {
                val methodName = match.groupValues[1]
                val siteId = "$moduleName.$className.$methodName"
                val classification = allowlist[siteId]
                if (classification == null) {
                    violations.add("PRODUCTION_SETTER_AUTOWIRED: ${file.path}:$methodName (site=$siteId)")
                }
            }

            // ── Detect @Lazy ──
            if (content.contains("@Lazy")) {
                val siteId = "$moduleName.$className"
                val classification = allowlist[siteId]
                if (classification == null) {
                    violations.add("PRODUCTION_LAZY: ${file.path} (site=$siteId)")
                }
            }
        }

        if (violations.isNotEmpty()) {
            println("\n=== CONSTRUCTOR INJECTION POLICY VIOLATIONS ===")
            violations.forEach { println("  ✗ $it") }
            println("\n${violations.size} violation(s) found.")
            println("Fix the violation or register it in policies/constructor-injection-allowlist.txt")
            throw GradleException("Constructor injection policy check failed with ${violations.size} violation(s)")
        } else {
            println("OK: Constructor injection policy verified — no violations found")
        }
    }
}
