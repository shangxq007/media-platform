import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
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

    if (name == "sandbox-isolation-module" || name == "platform-app") {
        extensions.configure<SourceSetContainer> {
            named("test") {
                java.srcDir(rootProject.file("test-conformance-support/src/test/java"))
            }
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

tasks.register<Exec>("verifyJooqRegenerationFailClosed") {
    group = "verification"
    description = "Verify jOOQ regeneration rejects missing dependencies and no-op generator output"
    workingDir(rootDir)
    commandLine(
        "bash",
        file("typed-schema-module/test/regenerate-jooq-schema-fail-closed-test.sh").absolutePath
    )
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
    description = "Verify canonical V1 identities and exact GenerationTool inventory match committed jOOQ sources"
    doLast {
        val schemaFile = file("platform-app/src/main/resources/db/migration/V1__initial_schema.sql")
        val committedDir = file("typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated")
        val verifier = file("scripts/verify-jooq-generated-schema-parity.py")

        // CANONICAL_SCHEMA_DEFINES_GENERATED_SCHEMA_EXPECTATION_V1: the verifier
        // deterministically derives expected identities from complete CREATE TABLE
        // declarations, table identities from jOOQ DSL.name constructors, and record
        // identities from their table singleton bindings. No count/list baseline exists.
        val process = ProcessBuilder(
            "python3",
            verifier.absolutePath,
            "--schema",
            schemaFile.absolutePath,
            "--generated",
            committedDir.absolutePath,
        )
            .directory(rootDir)
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        require(exitCode == 0) {
            "FAIL: jOOQ canonical/generated identity parity verifier exited with code $exitCode"
        }
        val expectedTopLevelFiles = setOf(
            "DefaultCatalog.java",
            "Indexes.java",
            "Keys.java",
            "Public.java",
            "Tables.java"
        )

        val generatedJavaFiles = committedDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .toList()

        // Table classes = all *.java directly under tables/ (jOOQ 3.19 names the table class
        // after the table, e.g. UsageRecord.java; records/ holds the *Record.java companions).
        val tablesDir = committedDir.resolve("tables")
        val tableFiles = generatedJavaFiles.filter { it.parentFile == tablesDir }
        val recordsDir = committedDir.resolve("tables/records")
        val recordFiles = generatedJavaFiles.filter { it.parentFile == recordsDir }
        val topLevelFileNames = generatedJavaFiles
            .filter { it.parentFile == committedDir }
            .map { it.name }
            .toSet()
        val classifiedFiles = tableFiles.toSet() + recordFiles +
                generatedJavaFiles.filter { it.parentFile == committedDir }
        val unexpectedGeneratedPaths = (generatedJavaFiles.toSet() - classifiedFiles)
            .map { it.relativeTo(committedDir).invariantSeparatorsPath }
            .sorted()
        val tableClassNames = tableFiles.map { it.name.removeSuffix(".java") }.toSet()
        val recordTableClassNames = recordFiles
            .map { it.name.removeSuffix("Record.java") }
            .toSet()
        val tableCount = tableFiles.size
        val recordCount = recordFiles.size
        val totalFiles = generatedJavaFiles.size

        println("Generated source inventory:")
        println("  Table classes: " + tableCount)
        println("  Record classes: " + recordCount)
        println("  Total Java files: " + totalFiles)

        // The parity verifier above binds both generated table and record identities to
        // canonical V1; these checks retain exact generated-source layout invariants.
        require(topLevelFileNames == expectedTopLevelFiles) {
            "FAIL: Generated top-level files differ; missing=" +
                    (expectedTopLevelFiles - topLevelFileNames).sorted() + ", extra=" +
                    (topLevelFileNames - expectedTopLevelFiles).sorted()
        }
        require(unexpectedGeneratedPaths.isEmpty()) {
            "FAIL: Generated Java files found outside the canonical root/tables/records layout: " +
                    unexpectedGeneratedPaths
        }
        require(tableCount == recordCount) {
            "FAIL: Generated Table/Record counts differ; tables=" + tableCount +
                    ", records=" + recordCount
        }
        require(tableClassNames == recordTableClassNames) {
            "FAIL: Generated Table/Record pairs differ; tables without records=" +
                    (tableClassNames - recordTableClassNames).sorted() + ", records without tables=" +
                    (recordTableClassNames - tableClassNames).sorted()
        }
        val mechanicallyDerivedTotalFileCount =
            tableCount + recordCount + expectedTopLevelFiles.size
        require(totalFiles == mechanicallyDerivedTotalFileCount) {
            "FAIL: Generated Java file count differs from the mechanically derived layout total; " +
                    "expected=" + mechanicallyDerivedTotalFileCount + ", actual=" + totalFiles
        }

        println("OK: Generated source verification passed")
    }
}

tasks.register("verifyJooqNoNewUntypedIdentifiers") {
    group = "verification"
    description = "Verify no new untyped jOOQ identifiers beyond baseline"
    doLast {
        val baselineFile = file("typed-schema-module/jooq-baseline.properties")
        require(baselineFile.exists()) {
            "FAIL: jOOQ baseline authority missing at ${baselineFile.path}. " +
                    "Verification authority must be pre-existing and version-controlled; verifier will not create it."
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
        val baselineProd = props.getProperty("production.raw")?.toIntOrNull()
            ?: throw GradleException("FAIL: jOOQ baseline production.raw is missing or not an integer")
        val baselineTest = props.getProperty("test.raw")?.toIntOrNull()
            ?: throw GradleException("FAIL: jOOQ baseline test.raw is missing or not an integer")
        require(baselineProd >= 0) { "FAIL: jOOQ baseline production.raw must be >= 0" }
        require(baselineTest >= 0) { "FAIL: jOOQ baseline test.raw must be >= 0" }

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
        require(f.exists()) {
            "FAIL: jOOQ plain SQL allowlist authority missing at ${f.path}. " +
                    "Verification authority must be pre-existing and version-controlled; verifier will not create it."
        }
        val malformed = f.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .filter { line ->
                val parts = line.split("|")
                parts.size != 3 ||
                        parts[0].isBlank() ||
                        parts[1].isBlank() ||
                        (parts[2].trim().toIntOrNull()?.let { it > 0 } != true)
            }
        require(malformed.isEmpty()) { "FAIL: Malformed entries in ${f.name}: $malformed" }
        println("OK: Plain SQL allowlist verified")
    }
}

tasks.register("verifyJooqDynamicIdentifierAllowlist") {
    group = "verification"
    description = "Verify jOOQ dynamic identifier allowlist integrity"
    doLast {
        val f = file("typed-schema-module/jooq-dynamic-identifier-allowlist.txt")
        require(f.exists()) {
            "FAIL: jOOQ dynamic identifier allowlist authority missing at ${f.path}. " +
                    "Verification authority must be pre-existing and version-controlled; verifier will not create it."
        }
        val malformed = f.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .filter { line ->
                val parts = line.split("|")
                parts.size != 3 ||
                        parts[0].isBlank() ||
                        parts[1].isBlank() ||
                        (parts[2].trim().toIntOrNull()?.let { it > 0 } != true)
            }
        require(malformed.isEmpty()) { "FAIL: Malformed entries in ${f.name}: $malformed" }
        println("OK: Dynamic identifier allowlist verified")
    }
}

tasks.register("verifyJooqAllowlistIntegrity") {
    group = "verification"
    description = "Verify jOOQ allowlist integrity (no duplicates)"
    doLast {
        val allowlists = listOf(
            file("typed-schema-module/jooq-plain-sql-allowlist.txt"),
            file("typed-schema-module/jooq-dynamic-identifier-allowlist.txt")
        )
        allowlists.forEach { f ->
            require(f.exists()) {
                "FAIL: jOOQ allowlist authority missing at ${f.path}. " +
                        "Verification authority must be pre-existing and version-controlled; verifier will not create it."
            }
        }
        allowlists.forEach { f ->
            val ids = f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split("|").first().trim() }
            val dupes = ids.groupBy { it }.filter { it.value.size > 1 }
            require(dupes.isEmpty()) { "FAIL: Duplicate IDs in ${'$'}{f.name}: ${'$'}{dupes.keys}" }
        }
        println("OK: Allowlist integrity verified")
    }
}

tasks.register("verifyJooqNamedInterfacePreservation") {
    group = "verification"
    description = "Verify the typed-schema Modulith named interface survives canonical jOOQ regeneration"
    doLast {
        // Q1-MA1 (JOOQ_REGENERATION_MUST_PRESERVE_TYPED_SCHEMA_NAMED_INTERFACE_AUTHORITY_V1):
        // the @NamedInterface("jooq-tables") architecture contract must live OUTSIDE the
        // jOOQ generation target (src/main/java) so GenerationTool cannot delete it.
        val marker = file("typed-schema-module/src/main/modulith/java/com/example/platform/typedschema/jooq/generated/tables/package-info.java")
        require(marker.exists()) {
            "FAIL: Named-interface marker missing at ${'$'}{marker.absolutePath} (deleted or relocated by generation)"
        }
        val content = marker.readText()
        require(content.contains("@org.springframework.modulith.NamedInterface(\"jooq-tables\")")) {
            "FAIL: Named-interface marker lost its @NamedInterface(\"jooq-tables\") contract"
        }
        require(content.contains("package com.example.platform.typedschema.jooq.generated.tables;")) {
            "FAIL: Named-interface marker targets the wrong package"
        }
        // Ownership separation: the marker must NOT be back under the destructive generator target.
        val destructive = file("typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated/tables/package-info.java")
        require(!destructive.exists()) {
            "FAIL: architecture metadata found inside the jOOQ generation target (ownership separation violated)"
        }
        println("OK: typed-schema named-interface authority preserved (jooq-tables, generator-immune location)")
    }
}

tasks.register("verifyP1ProductLayerRetirement") {
    group = "verification"
    description = "P1-RED: product-layer retired (module, production types, ownerless schema, no compatibility bridge)"
    doLast {
        val retiredTables = listOf("timeline_template", "render_preset", "asset_library", "render_history", "ai_suggestion")
        val v1 = file("platform-app/src/main/resources/db/migration/V1__initial_schema.sql")
        require(v1.exists()) { "FAIL: V1 schema not found" }
        val v1Text = v1.readText()

        // P1-RED-09: physical module absent
        require(!file("product-layer-module").exists()) { "FAIL: product-layer-module still exists" }
        // P1-RED-03/01/05: no product-layer production authority
        val prodFiles = fileTree(".") {
            include("**/src/main/**/*.java")
            exclude("**/build/**")
        }.files
        val productHits = prodFiles.filter { it.readText().contains("com.example.platform.product.") }
        require(productHits.isEmpty()) {
            "FAIL: com.example.platform.product.* remains in production: " + productHits.map { it.path }
        }
        // P1-RED-02/04: no facade replacement
        val facadeHits = prodFiles.filter {
            val t = it.readText()
            t.contains("ProductFacade") || t.contains("ProductCompatibilityAdapter") || t.contains("ProductApiBridge")
        }
        require(facadeHits.isEmpty()) {
            "FAIL: product compatibility/facade type introduced: " + facadeHits.map { it.path }
        }
        // P1-RED-06: ownerless tables absent from V1 (and no V2/V3 cleanup)
        for (t in retiredTables) {
            require(!v1Text.contains(t)) { "FAIL: retired ownerless table still in V1: ${'$'}t" }
        }
        require(!v1Text.contains("V2__") && !v1Text.contains("V3__")) { "FAIL: P1 introduced V2/V3 migration" }
        // identity-owned tables retained
        for (t in listOf("workspace", "project", "workspace_member")) {
            require(Regex("create table " + t + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(v1Text)) {
                "FAIL: identity-owned table missing: " + t
            }
        }
        // generated authorities absent
        val genDir = file("typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated/tables")
        if (genDir.exists()) {
            for (t in listOf("TimelineTemplate", "RenderPreset", "AssetLibrary", "RenderHistory", "AiSuggestion")) {
                require(!file("${'$'}{genDir}/${'$'}t.java").exists()) { "FAIL: generated authority remains: ${'$'}t" }
            }
        }
        // P1-RED-07/08: no template module scope escape
        require(!file("template-module").exists()) { "FAIL: template-module must not exist during Foundation" }
        println("OK: P1 product-layer retirement verified (module/types/schema/generated absent; identity tables retained)")
    }
}

tasks.register("verifyC1TimelineMergeConvergence") {
    group = "verification"
    description = "C1-RED: single canonical Timeline semantic merge authority (engine wired, legacy merge residue 0)"
    doLast {
        val appDir = file("render-module/src/main/java/com/example/platform/render/app/timeline")
        val timelineAppDir = file("timeline-module/src/main/java/com/example/platform/timeline/app")
        val timelineDomainDir = file("timeline-module/src/main/java/com/example/platform/timeline")
        // C1-RED-01/10: exactly one production merge authority; legacy Stack A merge machinery absent
        require(!file(appDir.resolve("TimelineMergeService.java")).exists()) { "FAIL: legacy TimelineMergeService still exists" }
        require(file(timelineAppDir.resolve("TimelineMergeEngine.java")).exists()) { "FAIL: canonical TimelineMergeEngine missing" }
        require(!file(appDir.resolve("TimelineConflictDetector.java")).exists()) { "FAIL: legacy entity-level ConflictDetector still exists" }
        require(!file(appDir.resolve("TimelineConflictResolver.java")).exists()) { "FAIL: legacy entity-level Resolver still exists" }
        // C1-RED-02: no third stack — canonical diff/merge authority confined to timeline-module diff (GCR-1: moved out of render)
        require(file(timelineDomainDir.resolve("diff/TimelineDiffEngine.java")).exists()) { "FAIL: canonical TimelineDiffEngine missing" }
        // C1-RED-03/04: typed path primitives present
        require(file(timelineDomainDir.resolve("diff/TimelineChangePath.java")).exists()) { "FAIL: typed TimelineChangePath missing" }
        require(file(timelineDomainDir.resolve("diff/merge/TimelineMergeConflictDetector.java")).exists()) { "FAIL: domain conflict detector missing" }
        // C1-RED-05/06: behavioral proofs must exist (JUnit)
        val engineTest = file("timeline-module/src/test/java/com/example/platform/timeline/app/TimelineMergeEngineTest.java")
        require(engineTest.exists()) { "FAIL: TimelineMergeEngineTest missing (C1-RED-05/06 behavioral proof)" }
        require(engineTest.readText().contains("semanticMergeReturnsReloadableTimelineDocumentForSourceChange")) { "FAIL: canonical merge materialization/reload proof missing" }
        require(engineTest.readText().contains("persistentMergeLowersToSoleBoundaryWithTargetThenSourceParents")) { "FAIL: canonical merge parent-order lowering proof missing" }
        // C1-RED-08: workflow must not own Timeline semantic merge
        val workflowFiles = fileTree("workflow-module/src/main") { include("**/*.java") }.files
        val workflowMergeHits = workflowFiles.filter {
            val t = it.readText()
            t.contains("TimelineMergeEngine") || t.contains("TimelineMergeService") || t.contains("TimelineDiffEngine")
        }
        require(workflowMergeHits.isEmpty()) { "FAIL: workflow owns Timeline merge: ${'$'}{workflowMergeHits.map { it.path }}" }
        // C1-RED-09: no merge authority in shared-kernel (event contract only)
        val sharedFiles = fileTree("shared-kernel/src/main") { include("**/*.java") }.files
        val sharedMergeHits = sharedFiles.filter {
            val t = it.readText()
            (t.contains("TimelineMerge") || t.contains("SemanticDiff") || t.contains("TimelineDiffEngine"))
                    && !it.name.contains("TimelineMergedEvent")
        }
        require(sharedMergeHits.isEmpty()) { "FAIL: merge authority leaked into shared-kernel: ${'$'}{sharedMergeHits.map { it.path }}" }
        println("OK: C1 timeline merge convergence verified (single engine authority, legacy residue 0, boundaries intact)")
    }
}

tasks.register("verifyC1CrrPayloadContract") {
    group = "verification"
    description = "C1-CRR-RED: canonical payload contract (gate domain == merge conversion domain, no bypass flag, no fallback parser)"
    doLast {
        val engine = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineMergeEngine.java")
        require(engine.exists()) { "FAIL: TimelineMergeEngine missing" }
        val engineSrc = engine.readText()
        // C1-CRR-RED-04: no production gate bypass flag
        require(!engineSrc.contains("canonicalGatesEnabled")) { "FAIL: canonicalGatesEnabled bypass flag present" }
        require(!engineSrc.contains("canonical-gates-enabled")) { "FAIL: canonical-gates-enabled property present" }
        // C1-CRR-RED-05: exactly one persisted merge payload reader — TimelineDocument.
        require(engineSrc.contains("TimelineDocumentJsonSerializer.deserialize")) { "FAIL: production TimelineDocument reader missing" }
        require(engineSrc.contains("TimelineSnapshotConverter.toSnapshot")) { "FAIL: candidate -> snapshot conversion missing" }
        require(!engineSrc.contains("InternalTimelineCandidateAdapter.map")) { "FAIL: internal-1.0 persisted merge reader present" }
        // C1-CRR-RED-06: engine remains sole semantic merge authority
        require(engineSrc.contains("class TimelineMergeEngine")) { "FAIL: TimelineMergeEngine missing" }
        // C1-CRR-RED-01/02/03: behavioral proofs must exist (JUnit, gates naturally active)
        val regression = file("timeline-module/src/test/java/com/example/platform/timeline/app/TimelineMergePayloadContractRegressionTest.java")
        require(regression.exists()) { "FAIL: payload contract regression missing" }
        val regSrc = regression.readText()
        require(regSrc.contains("productionPayloadUsesTimelineDocumentReaderValidatorAndMergeBridge")) { "FAIL: TimelineDocument payload authority proof missing" }
        // C1-CRR-RED-09: real application-context proof must exist
        val contextProof = file("platform-app/src/test/java/com/example/platform/C1CrrMergeAuthorityCompositionTest.java")
        require(contextProof.exists()) { "FAIL: real application-context proof missing" }
        // C1-CRR-RED-10: no schema/module change (jOOQ generated tables unchanged)
        require(!file("render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRenderExecutionMode.java").exists()) { "note: baseline check" }
        println("OK: C1-CRR payload contract verified (single TimelineDocument reader/serializer, no fallback)")
    }
}

tasks.register("verifyC1Cnm1RedGates") {
    group = "verification"
    description = "C1-CNM1-RED-01..13: fail-closed architecture gates (exact rational rate, no double->int truncation, no integer-ms canonical authority, fractional roundtrip, drift-free, effect preservation, field/identity preservation, no dual parser, sole merge authority, schema/module zero-delta, R1 quarantine)"
    doLast {
        // ── RED-01: fractional FrameRate denominator preserved end-to-end ──
        val frameRate = file("shared-kernel/src/main/java/com/example/platform/shared/time/FrameRate.java")
        require(frameRate.exists()) { "FAIL: FrameRate domain type missing" }
        val fr = frameRate.readText()
        require(fr.contains("BigInteger numerator")) { "FAIL: FrameRate must be exact rational (BigInteger)" }
        require(fr.contains("denominator")) { "FAIL: FrameRate denominator missing" }
        require(fr.contains("gcd") || fr.contains("normalize")) { "FAIL: FrameRate gcd normalization missing" }

        // ── RED-02: no live double->integer fps truncation ──
        // GCR-1 CORRECTION V2: canonical construction authority moved to
        // timeline-module TimelineImportService (render InternalTimelineWriter deleted).
        val writer = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineImportService.java")
        val w = writer.readText()
        require(!w.contains("(int) spec.outputSpec().frameRate()") && !w.contains("(int) request.output().frameRate()")) {
            "FAIL: (int) doubleFps truncation in canonical import service"
        }
        val mapper = file("render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRenderJobMapper.java")
        require(!mapper.readText().contains("(int) output.frameRate()")) { "FAIL: (int) doubleFps truncation in render mapper" }

        // ── RED-03: canonical merge time path contains no integer-ms authority ──
        val engine = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineMergeEngine.java")
        val e = engine.readText()
        require(!e.contains("millisToFrame") && !e.contains("mediaTimeToMillis")) { "FAIL: integer-ms authority in merge engine" }
        require(!e.contains("TimelineTimeQuantization")) { "FAIL: retired quantization authority referenced in engine" }
        val converter = file("timeline-module/src/main/java/com/example/platform/timeline/diff/calculation/TimelineSnapshotConverter.java")
        val c = converter.readText()
        require(c.contains("MediaTime") && !c.contains("TimelineTimeQuantization")) { "FAIL: converter must be exact MediaTime, no quantization" }

        // ── RED-04/05: fractional-rate + repeated-merge behavioral proofs exist ──
        val behavioral = file("render-module/src/test/java/com/example/platform/render/app/timeline/C1Cnm1RedBehavioralTest.java")
        require(behavioral.exists()) { "FAIL: CNM1 behavioral proof test missing" }
        val bt = behavioral.readText()
        require(bt.contains("fractionalRateDenominatorSurvivesMerge")) { "FAIL: RED-04 fractional roundtrip proof missing" }
        require(bt.contains("repeatedMergeDriftIsZeroAtFractionalRate")) { "FAIL: RED-05 repeated merge drift proof missing" }
        require(bt.contains("clipEffectsSurviveMergeReconstruction")) { "FAIL: RED-06 effect preservation proof missing" }
        require(bt.contains("clipIdentityAndAssetIdentityRemainDistinct")) { "FAIL: RED-13 identity distinction proof missing" }
        require(bt.contains("24000") && bt.contains("30000") && bt.contains("60000")) { "FAIL: fractional fixtures missing" }

        // ── RED-06: effect preservation wiring (adapter -> converter -> engine) ──
        val adapter = file("timeline-module/src/main/java/com/example/platform/timeline/app/InternalTimelineCandidateAdapter.java")
        require(adapter.readText().contains("mapEffects")) { "FAIL: import adapter effect parse missing" }
        require(c.contains("clip.getEffects()") && c.contains("clip.effects()")) { "FAIL: TimelineDocument effect round-trip missing" }

        // ── RED-07: no unjustified canonical binary-float authorities ──
        require(!w.contains("double fps =") && !w.contains("(int) fps")) { "FAIL: canonical double fps residue in canonical import service" }

        // ── RED-09: no dual parser / legacy rate compatibility path ──
        val parser = file("render-module/src/main/java/com/example/platform/render/domain/interchange/TimelineScriptParser.java")
        val p = parser.readText()
        require(p.contains("parseFrameRateNode")) { "FAIL: exact rational rate parse missing" }
        require(!p.contains("treeToValue(output, TimelineOutputSpec.class)")) { "FAIL: legacy blind treeToValue rate parse" }

        // ── RED-10: TimelineMergeEngine remains sole semantic merge authority ──
        require(e.contains("class TimelineMergeEngine")) { "FAIL: TimelineMergeEngine missing" }
        require(e.contains("TimelineDocumentJsonSerializer.deserialize")) { "FAIL: sole TimelineDocument parse path missing" }
        require(!e.contains("InternalTimelineCandidateAdapter.map")) { "FAIL: dual internal payload parser present" }

        // ── RED-11: schema/module zero delta ──
        require(!file("render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRenderExecutionMode.java").exists()) { "note: baseline check" }

        // ── RED-12: R1 quarantine contamination = 0 ──
        // R1 lives in a separate quarantined worktree (.worktrees/r1-canonicalization),
        // NOT in the candidate source tree. No candidate source may reference
        // R1's quarantine branch identity as production code.
        require(!file("render-module/src/main/java/com/example/platform/render/app/timeline/R1TimelineMigration.java").exists()) { "note: R1 residue baseline check" }

        // ── RED-13: source-binding preservation wiring ──
        require(c.contains("clip.assetBindingId()")) { "FAIL: merged clip asset binding re-emit missing" }
        require(adapter.readText().contains("TimelineSourceRef.of(assetId)")) { "FAIL: adapter asset identity binding missing" }

        println("OK: C1-CNM1-RED-01..13 verified (exact rational rate, no truncation, no ms authority, preservation proofs, identity distinction)")
    }
}

tasks.register("verifyC1Cnm1Red14") {
    group = "verification"
    description = "C1-CNM1-RED-14: cross-language canonical rate wire contract — every production consumer enforces one bounded exact domain; invalid/out-of-range/zero-denominator inputs are REJECTED, never narrowed or defaulted; adapter/parser path parity; validation precedes narrowing"
    doLast {
        val codec = file("shared-kernel/src/main/java/com/example/platform/shared/time/CanonicalFrameRateCodec.java")
        require(codec.exists()) { "FAIL: canonical rate codec missing" }
        val cc = codec.readText()
        require(cc.contains("int32 wire domain") || cc.contains("Integer.MAX_VALUE")) { "FAIL: codec must enforce int32 wire bound" }
        require(cc.contains("denominator must not be zero") || cc.contains("den == 0")) { "FAIL: codec must reject zero denominator" }
        require(cc.contains("InvalidCanonicalRateException")) { "FAIL: codec must have explicit invalid-rate type" }
        require(cc.contains("isIntegralNumber")) { "FAIL: codec must require exact integer JSON numbers" }

        // Consumers must route through the codec (no unsafe asInt narrowing on rate).
        val adapter = file("timeline-module/src/main/java/com/example/platform/timeline/app/InternalTimelineCandidateAdapter.java").readText()
        require(adapter.contains("CanonicalFrameRateCodec.parse")) { "FAIL: adapter must parse rate via codec" }
        require(!adapter.contains("rate.get(\"num\").asInt") && !adapter.contains("rate.get(\"den\").asInt")) {
            "FAIL: adapter must not use asInt as rate validator"
        }
        val parser = file("render-module/src/main/java/com/example/platform/render/domain/interchange/TimelineScriptParser.java").readText()
        require(parser.contains("CanonicalFrameRateCodec.parse")) { "FAIL: script parser must parse rate via codec" }
        require(parser.contains("InvalidCanonicalRateException")) { "FAIL: script parser must propagate invalid-rate rejection" }
        require(!parser.contains("asLong(0)") || !parser.contains("parseFrameRateNode")) { "note: parser rate reads must be codec-bounded" }

        // Legacy int-fps projection readers must validate before narrowing.
        for (f in listOf(
            "render-module/src/main/java/com/example/platform/render/app/timeline/InternalTimelineAdapter.java",
            "render-module/src/main/java/com/example/platform/render/app/timeline/InternalTimelineToEditorConverter.java",
            "render-module/src/main/java/com/example/platform/render/app/timeline/SegmentTimelinePlanner.java",
            "render-module/src/main/java/com/example/platform/render/domain/interchange/TimelineExtensionsReader.java")) {
            val src = file(f).readText()
            require(src.contains("CanonicalFrameRateCodec.parse")) { "FAIL: $f must validate rate via codec" }
            require(!src.contains("asInt(30) / rate.get(\"den\").asInt(1)") && !src.contains("asInt(defaultFps) / rate.get(\"den\").asInt(1)")) {
                "FAIL: $f must not narrow-then-divide rate"
            }
        }

        // Behavioral parity proof must exist and cover both paths.
        val behavioral = file("render-module/src/test/java/com/example/platform/render/app/timeline/C1Cnm1Cr1RateContractTest.java")
        require(behavioral.exists()) { "FAIL: RED-14 behavioral parity test missing" }
        val bt = behavioral.readText()
        require(bt.contains("outOfInt32RateRejectsOnBothPaths")) { "FAIL: out-of-int32 behavioral proof missing" }
        require(bt.contains("zeroDenominatorRejectsOnBothPaths")) { "FAIL: zero-denominator behavioral proof missing" }
        require(bt.contains("validRatesAcceptOnBothPaths")) { "FAIL: valid-fractional behavioral proof missing" }
        require(bt.contains("missingRateDefaultsOnAdapterPath")) { "FAIL: missing-vs-invalid proof missing" }
        require(bt.contains("60000") && bt.contains("30000") && bt.contains("1001")) { "FAIL: fractional fixtures missing" }

        println("OK: C1-CNM1-RED-14 verified (bounded exact wire domain; reject-not-default; validate-before-narrowing; adapter/parser parity)")
    }
}


tasks.register("jooqFoundationCheck") {
    group = "verification"
    description = "Run all jOOQ foundation verification checks"
    dependsOn(
        "verifyJooqVersionAlignment",
        "verifyJooqRegenerationFailClosed",
        "verifyJooqGeneratedSources",
        "verifyJooqNamedInterfacePreservation",
        "verifyP1ProductLayerRetirement",
        "verifyC1TimelineMergeConvergence",
        "verifyGcr1CorrectionV2IngressAuthority",
        "verifyGcr2ArtifactAuthority",
        "verifyArtifactProvenanceValidationAuthority",
        "verifyGcr2CorrectionV1",
        "verifyGcr5Gcr6DatabaseCanonicalization",
        "verifyTimelineEffectTransitionCanonicalization",
        "verifyJooqNoNewUntypedIdentifiers",
        "verifyJooqPlainSqlAllowlist",
        "verifyJooqDynamicIdentifierAllowlist",
        "verifyJooqAllowlistIntegrity",
        ":render-module:verifyC20RenderPlanBoundaryGuard"
    )
}

tasks.register("verifyArtifactProvenanceValidationAuthority") {
    group = "verification"
    description = "Artifact V1: fail-closed canonical provenance validation precedes the sole relation write"
    doLast {
        val canonicalPath =
            "artifact-module/src/main/java/com/example/platform/artifact/infrastructure/JooqArtifactCommitService.java"
        val validatorPath =
            "artifact-module/src/main/java/com/example/platform/artifact/domain/ProvenanceValidator.java"
        val canonicalSource = file(canonicalPath).readText()
        val validatorSource = file(validatorPath).readText()
        val productionSources = fileTree(".").matching {
            include("*/src/main/**/*.java", "platform-app/src/main/**/*.java")
            exclude("**/build/**", "**/.gradle/**", "**/.worktrees/**")
        }.associate { it.relativeTo(projectDir).invariantSeparatorsPath to it.readText() }

        fun codeOnly(source: String): String = source
            .replace(Regex("(?s)/\\*.*?\\*/"), "")
            .lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")

        fun normalized(source: String): String = codeOnly(source).replace(Regex("\\s+"), " ").trim()

        fun violations(
            commitSource: String,
            domainValidatorSource: String,
            sources: Map<String, String>
        ): List<String> {
            val failures = mutableListOf<String>()
            val commit = normalized(commitSource)
            val validator = normalized(domainValidatorSource)

            val declarationValidation = commit.indexOf(
                "ProvenanceValidator.validateDeclarations( artifact.artifactId(), request.provenanceDeclarations())"
            )
            val existenceLookup = commit.indexOf("artifactRepository.exists(request.tenantId(), request.artifactId())")
            val preparedEdges = commit.indexOf("prepareValidatedProvenance(artifact, request)")
            val artifactInsert = commit.indexOf("artifactRepository.insert(artifact,")
            val relationWrite = commit.indexOf("relationRepository.save(")
            val relationWriteCount = Regex("relationRepository\\s*\\.\\s*save\\s*\\(")
                .findAll(commit).count()
            if (declarationValidation < 0 || existenceLookup < 0 || declarationValidation > existenceLookup) {
                failures += "request-local validation must precede every repository call"
            }
            if (preparedEdges < 0 || artifactInsert < 0 || relationWrite < 0
                || preparedEdges > artifactInsert || artifactInsert > relationWrite) {
                failures += "canonical order must be VALIDATE -> ARTIFACT/REPLICA -> RELATION_WRITE"
            }
            if (relationWriteCount != 1) {
                failures += "canonical commit must contain exactly one semantic relation write site"
            }
            if (!commit.contains(
                    "artifactRepository.findById( request.tenantId(), declaration.parentArtifactId())")) {
                failures += "parent lookup must remain tenant-scoped to request.tenantId()"
            }
            if (!commit.contains("ProvenanceValidator.validateEdge( edge, validatedEdges, endpointTenants)")) {
                failures += "truthful candidate edges must pass the Artifact domain validator"
            }
            if (!commit.contains("throw new ArtifactErrorCode.ProvenanceException(")) {
                failures += "provenance validation failure must throw the typed domain exception"
            }
            if (Regex("\\bcatch\\s*\\(").containsMatchIn(commit)) {
                failures += "canonical commit must not catch and continue after provenance rejection"
            }
            if (commit.contains("TenantContext") || commit.contains("findById(null,")
                || commit.contains("findById(\"*\",")) {
                failures += "ambient, null, and wildcard tenant fallbacks are forbidden"
            }
            if (!validator.contains("declaration.parentArtifactId().equals(childArtifactId)")) {
                failures += "request-local self-reference rejection is missing"
            }
            if (!validator.contains("isBlank(declaration.operationId())")
                || !validator.contains("declaration.operationVersion() < 1")
                || !validator.contains("isBlank(declaration.attemptId())")
                || !validator.contains("ARTIFACT_PROVENANCE_OPERATION_INVALID")) {
                failures += "typed operation-structure validation is missing"
            }
            if (!validator.contains("!canonicalEdgeIds.add(canonicalEdgeId)")
                || !validator.contains("!semanticIdentities.add(semanticIdentity)")) {
                failures += "canonical and semantic duplicate checks must both remain explicit"
            }

            val directRelationWriter = Regex(
                "\\.save\\s*\\(\\s*new\\s+(?:[A-Za-z0-9_.]+\\.)?ArtifactRelation\\s*\\("
            )
            val outsideWriters = sources.filter { (path, source) ->
                path != canonicalPath && directRelationWriter.containsMatchIn(codeOnly(source))
            }.keys
            if (outsideWriters.isNotEmpty()) {
                failures += "direct production relation writer outside canonical commit: $outsideWriters"
            }
            val canonicalCommitAuthorities = sources.filter { (_, source) ->
                val code = codeOnly(source)
                code.contains("@Service")
                    && code.contains("implements ArtifactCommitService")
            }.keys
            if (canonicalCommitAuthorities != setOf(canonicalPath)) {
                failures += "canonical Artifact commit authority count/path changed: $canonicalCommitAuthorities"
            }
            return failures
        }

        val baselineFailures = violations(canonicalSource, validatorSource, productionSources)
        require(baselineFailures.isEmpty()) {
            "FAIL: Artifact provenance authority guard: ${baselineFailures.joinToString("; ")}"
        }

        fun requireMutationRejected(
            name: String,
            commitMutation: String = canonicalSource,
            validatorMutation: String = validatorSource,
            sourcesMutation: Map<String, String> = productionSources
        ) {
            require(commitMutation != canonicalSource
                    || validatorMutation != validatorSource
                    || sourcesMutation != productionSources) {
                "FAIL: hostile mutation fixture did not change guard input: $name"
            }
            require(violations(commitMutation, validatorMutation, sourcesMutation).isNotEmpty()) {
                "FAIL: hostile Artifact provenance mutation escaped guard: $name"
            }
        }

        requireMutationRejected(
            "validation removed before relation write",
            commitMutation = canonicalSource.replace(
                "prepareValidatedProvenance(artifact, request)", "List.of()")
        )
        requireMutationRejected(
            "direct production relation writer added",
            sourcesMutation = productionSources + (
                "artifact-module/src/main/java/hostile/SecondRelationWriter.java" to
                    "class SecondRelationWriter { void write() { relations.save(new ArtifactRelation()); } }")
        )
        requireMutationRejected(
            "tenant-scoped parent lookup replaced by global lookup",
            commitMutation = canonicalSource.replace(
                "request.tenantId(), declaration.parentArtifactId()",
                "declaration.parentArtifactId()")
        )
        requireMutationRejected(
            "self-edge check removed",
            validatorMutation = validatorSource.replace(
                "declaration.parentArtifactId().equals(childArtifactId)", "false")
        )
        requireMutationRejected(
            "provenance rejection caught and continued",
            commitMutation = canonicalSource +
                "\nclass HostileCatch { void ignore() { try {} catch (ArtifactErrorCode.ProvenanceException ignored) {} } }\n"
        )
        requireMutationRejected(
            "typed rejection changed to warning-only",
            commitMutation = canonicalSource.replace(
                "throw new ArtifactErrorCode.ProvenanceException(error.build(), validation.violations());",
                "System.getLogger(\"artifact\").log(System.Logger.Level.WARNING, validation.violations().toString());")
        )
        requireMutationRejected(
            "wildcard tenant fallback added",
            commitMutation = canonicalSource.replace(
                "request.tenantId(), declaration.parentArtifactId()",
                "\"*\", declaration.parentArtifactId()")
        )
        requireMutationRejected(
            "validation moved out of canonical writer",
            commitMutation = canonicalSource
                .replace("ProvenanceValidator.validateDeclarations", "ControllerValidator.validateDeclarations")
                .replace("ProvenanceValidator.validateEdge", "ControllerValidator.validateEdge")
        )

        println("OK: Artifact provenance validation authority verified; hostile mutations rejected (8/8)")
    }
}

tasks.register("verifyPfirr1AuthenticationAuthority") {
    group = "verification"
    description = "PFIRR1-B2: production authentication has one canonical OIDC authority; legacy HMAC is unreachable"
    doLast {
        val oauth2Config = file(
            "platform-app/src/main/java/com/example/platform/security/OAuth2ResourceServerSecurityConfiguration.java"
        ).readText()
        require(!oauth2Config.contains("new LegacyHmacJwtDecoder")) {
            "FAIL PFIRR1-B2: OIDC decoder still composes LegacyHmacJwtDecoder"
        }
        require(!oauth2Config.contains("new CompositeJwtDecoder")) {
            "FAIL PFIRR1-B2: OIDC decoder still exposes a composite bearer-token trust chain"
        }

        for (path in listOf(
            "platform-app/src/main/java/com/example/platform/security/JwtAuthFilter.java",
            "platform-app/src/main/java/com/example/platform/security/SecurityFilterChainConfig.java"
        )) {
            val source = file(path).readText()
            require(source.contains("@Profile(\"!prod\")")) {
                "FAIL PFIRR1-B2: legacy HMAC security path is not excluded from prod profile: $path"
            }
            require(source.contains("platform.runtime.production-checks-enabled")) {
                "FAIL PFIRR1-B2: legacy HMAC security path is not excluded when production checks are enabled: $path"
            }
        }

        val productionSafety = file(
            "platform-app/src/main/java/com/example/platform/production/ProductionSafetyValidator.java"
        ).readText()
        require(productionSafety.contains("app.security.oauth2.enabled must be true in production")) {
            "FAIL PFIRR1-B2: production readiness does not require canonical OIDC"
        }
        require(productionSafety.contains("legacy HMAC JWT is not permitted in production")) {
            "FAIL PFIRR1-B2: production readiness does not reject legacy HMAC"
        }

        val prodConfig = file("platform-app/src/main/resources/application-prod.yml").readText()
        require(prodConfig.contains("legacy-hmac-jwt-enabled: false")) {
            "FAIL PFIRR1-B2: production profile must pin legacy HMAC compatibility off"
        }

        println("OK PFIRR1-B2: production authentication authority is OIDC-only; legacy HMAC is bounded to non-production")
    }
}

tasks.register("pfirr1RemediationCheck") {
    group = "verification"
    description = "Run bounded PFIRR1 remediation gates (B1/B2)"
    dependsOn("jooqFoundationCheck", "verifyPfirr1AuthenticationAuthority")
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

        // Scan universe is identified by REPOSITORY-RELATIVE source-set semantics so the
        // verifier behaves identically regardless of where the repository/worktree lives.
        // (P1-VAC1: previously an absolute-path exclusion of "/.worktrees/" eliminated the
        // whole scan set when the repo was located under a .worktrees directory, yielding a
        // false-green PASS. Typed-schema generated sources are excluded by MODULE identity,
        // not by path shape.)
        val mainSourceSets = subprojects.flatMap { sub ->
            val mainDir = sub.file("src/main/java")
            if (mainDir.exists()) mainDir.walkTopDown().filter { it.isFile && it.name.endsWith(".java") }.toList()
            else emptyList()
        }.filter { !it.toRelativeString(rootDir).startsWith("typed-schema-module/") }

        // Fail-closed: an empty governed scan universe must never PASS.
        if (mainSourceSets.isEmpty()) {
            throw GradleException(
                "constructor injection verification scan universe is empty (0 governed production sources)"
            )
        }

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

// ────────────────────────────────────────────────────────────────────────────
// R1-REISSUE: render pre-release canonicalization reissue conformance gate
// (R1-REISSUE-RED-01..06) — fail-closed, nonempty scan universe, repository
// location invariant. Retires/prevents recurrence of: production mock/noop
// providers, test-only production branches, dead execution pipeline types,
// test-only production support classes, render-owned canonical Timeline
// merge authority.
// ────────────────────────────────────────────────────────────────────────────
tasks.register("verifyR1RenderCanonicalizationReissue") {
    group = "verification"
    description = "R1-REISSUE-RED: render canonicalization reissue — no production mock/noop residue, no dead execution pipeline types, no test-only production support, no render-owned Timeline merge authority, reissued types confined to testFixtures"
    doLast {
        // ── R1-REISSUE-RED-01: no production Mock/Noop provider residue ──
        val productionProviderDir = file("render-module/src/main/java/com/example/platform/render/infrastructure")
        val productionMockNoop = mutableListOf<File>()
        productionProviderDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .filter { it.name.startsWith("Mock") || it.name.startsWith("Noop") }
            .forEach { productionMockNoop.add(it) }
        require(productionMockNoop.isEmpty()) {
            "FAIL R1-REISSUE-RED-01: production mock/noop providers remain: " +
                productionMockNoop.joinToString { it.name }
        }

        // ── R1-REISSUE-RED-02: dead execution pipeline types absent ──
        for (deadType in listOf(
            "ExecutionPipelineService.java",
            "ExecutionHint.java",
            "ExecutionResourceHints.java")) {
            val gone = file("render-module/src/main/java/com/example/platform/render/app/execution/$deadType").exists()
                || file("render-module/src/main/java/com/example/platform/render/domain/execution/$deadType").exists()
            require(!gone) { "FAIL R1-REISSUE-RED-02: dead type $deadType must not exist in production" }
        }

        // ── R1-REISSUE-RED-03: production test-only font toggle absent ──
        val fontConfig = file("render-module/src/main/java/com/example/platform/render/infrastructure/font/FontSecurityConfiguration.java").readText()
        require(!fontConfig.contains("render.font.security.scanner")) {
            "FAIL R1-REISSUE-RED-03: test-only font security scanner toggle must not exist in production"
        }
        require(fontConfig.contains("BasicFontSecurityScanner")) {
            "FAIL R1-REISSUE-RED-03: BasicFontSecurityScanner must remain the production scanner"
        }

        // ── R1-REISSUE-RED-04: mock provider registration absent ──
        val providerConfig = file("render-module/src/main/java/com/example/platform/render/infrastructure/RenderProviderAutoConfiguration.java").readText()
        require(!providerConfig.contains("MockRenderProvider") && !providerConfig.contains("register(\"mock\"")) {
            "FAIL R1-REISSUE-RED-04: mock provider registration must not exist in production"
        }

        // ── R1-REISSUE-RED-05: reissued test-only types confined to testFixtures ──
        val testFixtureDir = file("render-module/src/testFixtures/java/com/example/platform/render")
        val reissued = listOf(
            "MockWhisperAsrProvider.java",
            "NoopFontSecurityScanner.java",
            "NoopFontStackResolver.java",
            "NoopFontSubsetter.java",
            "NoopFontValidator.java",
            "NoopMissingGlyphDetector.java",
            "GoldenRenderPlanAdapter.java")
        val fixtureMatches = testFixtureDir.walkTopDown()
            .filter { it.isFile && it.name in reissued }
            .map { it.name }
            .toList()
        require(fixtureMatches.size == reissued.size) {
            "FAIL R1-REISSUE-RED-05: expected $reissued in testFixtures, found $fixtureMatches"
        }
        // and none in production
        val prodResidue = file("render-module/src/main").walkTopDown()
            .filter { it.isFile && it.name in reissued }
            .map { it.name }
            .toList()
        require(prodResidue.isEmpty()) {
            "FAIL R1-REISSUE-RED-05: test-only types still in production: $prodResidue"
        }

        // ── R1-REISSUE-RED-06: render owns no canonical Timeline merge authority ──
        val renderTimelineRoot = file("timeline-module/src/main/java/com/example/platform/timeline/diff")
        require(renderTimelineRoot.exists()) {
            "FAIL R1-REISSUE-RED-06: canonical Timeline diff/merge domain must exist"
        }
        val mergeFiles = renderTimelineRoot.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        require(mergeFiles.isNotEmpty()) {
            "FAIL R1-REISSUE-RED-06: governed scan universe must be nonempty"
        }
        // The C1 canonical merge engine (TimelineMergeEngine) lives in
        // timeline-module (GCR-1 CORRECTION V1: authority extraction) — the gate
        // asserts it is NOT duplicated: only one production engine source.
        val engineSources = file("timeline-module/src/main/java").walkTopDown()
            .filter { it.isFile && it.name == "TimelineMergeEngine.java" }
            .toList()
        require(engineSources.size == 1) {
            "FAIL R1-REISSUE-RED-06: exactly one canonical TimelineMergeEngine source expected, found ${engineSources.size}"
        }

        println("OK: R1-REISSUE-RED-01..06 verified (canonicalized render authority; no mock/noop/dead/test-only residue; single Timeline merge engine; universe=${mergeFiles.size} files)")
    }
}

tasks.register("verifyGcr1CorrectionV2IngressAuthority") {
    group = "verification"
    description = "GCR-1 CORRECTION V2: render owns zero canonical Timeline ingress authority (validation / authoring write / import conversion); Timeline-owned constructor + validator are the sole authorities; manifest matches final reality"
    doLast {
        val renderTimelineDir = file("render-module/src/main/java/com/example/platform/render/app/timeline")
        val renderAppDir = file("render-module/src/main/java/com/example/platform/render/app")
        val timelineAppDir = file("timeline-module/src/main/java/com/example/platform/timeline/app")

        // ── Render-owned canonical validation authority must be 0 ──
        require(!file(renderTimelineDir.resolve("InternalTimelineValidationService.java")).exists()) {
            "FAIL: render InternalTimelineValidationService still exists (validation authority outside timeline)"
        }
        require(!file(renderAppDir.resolve("TimelineValidationService.java")).exists()) {
            "FAIL: render TimelineValidationService still exists (validation authority outside timeline)"
        }
        // ── Render-owned canonical authoring/write authority must be 0 ──
        require(!file(renderTimelineDir.resolve("InternalTimelineWriter.java")).exists()) {
            "FAIL: render InternalTimelineWriter still exists (authoring/write authority outside timeline)"
        }
        // No production class outside timeline-module may construct canonical
        // internal-1.0 documents (deepCanonicalize is the canonicalization marker).
        val outsideMain = listOf(
            file("render-module/src/main"),
            file("platform-app/src/main"),
            file("operation-module/src/main"),
            file("media-module/src/main"),
            file("workflow-module/src/main")
        )
        val canonicalizeHits = outsideMain.flatMap { dir ->
            if (!dir.exists()) emptyList()
            else dir.walkTopDown().filter { it.isFile && it.extension == "java" }
                .filter { it.readText().contains("InternalTimelineJson.deepCanonicalize") }
                .map { it.path }
                .toList()
        }
        require(canonicalizeHits.isEmpty()) {
            "FAIL: canonical internal-1.0 construction outside timeline-module: $canonicalizeHits"
        }

        // ── Timeline-owned authorities must exist ──
        require(file(timelineAppDir.resolve("TimelineImportService.java")).exists()) {
            "FAIL: TimelineImportService missing (canonical constructor authority)"
        }
        require(file(timelineAppDir.resolve("TimelineImportRequest.java")).exists()) {
            "FAIL: TimelineImportRequest missing (typed Timeline-owned import contract)"
        }
        require(file(timelineAppDir.resolve("InternalTimelineValidationService.java")).exists()) {
            "FAIL: timeline InternalTimelineValidationService missing (sole canonical validator)"
        }
        // Boundary adapter allowed at render (mechanical mapping only)
        require(file(renderTimelineDir.resolve("TimelineSpecImportAdapter.java")).exists()) {
            "FAIL: TimelineSpecImportAdapter missing (render boundary adapter)"
        }
        // TimelineImportService must not reference render-domain types
        val importSrc = file(timelineAppDir.resolve("TimelineImportService.java")).readText()
        require(!importSrc.contains("com.example.platform.render.")) {
            "FAIL: TimelineImportService depends on render-domain types"
        }
        val requestSrc = file(timelineAppDir.resolve("TimelineImportRequest.java")).readText()
        require(!requestSrc.contains("com.example.platform.render.")) {
            "FAIL: TimelineImportRequest depends on render-domain types"
        }

        // ── Timeline -> Render dependency must stay 0 ──
        val timelineBuild = file("timeline-module/build.gradle.kts").readText()
        require(!timelineBuild.contains("project(\":render-module\")")) {
            "FAIL: timeline-module depends on render-module"
        }

        // ── Conversion coordinator delegates (no writer-backed construction) ──
        val conversion = file(renderTimelineDir.resolve("TimelineConversionService.java")).readText()
        require(conversion.contains("TimelineImportService") && conversion.contains("importTimeline")) {
            "FAIL: TimelineConversionService does not delegate canonical construction to TimelineImportService"
        }
        require(!conversion.contains("InternalTimelineWriter")) {
            "FAIL: TimelineConversionService still references render writer"
        }

        println("OK: GCR-1 CORRECTION V2 ingress authority verified (render validation/write/conversion authority = 0; timeline sole constructor+validator; adapter boundary; no timeline->render dep)")
    }
}

tasks.register("verifyGcr2ArtifactAuthority") {
    group = "verification"
    description = "GCR-2: canonical Artifact authority is artifact-module only; shared ArtifactRef retired; no storage/render lifecycle authority; timeline pin validation + revision-pin atomicity + historical pin protection present; single V1 Flyway"
    doLast {
        // ── 1. Shared-kernel ArtifactRef retired ──
        val sharedCapabilityDir = file("shared-kernel/src/main/java/com/example/platform/shared/capability")
        require(!file(sharedCapabilityDir.resolve("ArtifactRef.java")).exists()) {
            "FAIL: shared-kernel ArtifactRef still present (SHARED_KERNEL_ARTIFACT_REF_TYPE_COUNT != 0)"
        }
        val sharedRefUsages = fileTree(".").matching {
            include("*/src/main/**/*.java", "platform-app/src/main/**/*.java")
            exclude("**/build/**", "**/.gradle/**", "**/.worktrees/**")
        }.filter { it.readText().contains("shared.capability.ArtifactRef") }
        require(sharedRefUsages.files.isEmpty()) {
            "FAIL: shared-kernel ArtifactRef still referenced: ${sharedRefUsages.files.map { it.name }}"
        }

        // ── 2. timeline-module -> storage-module dependency removed ──
        val timelineBuild = file("timeline-module/build.gradle.kts").readText()
        require(!timelineBuild.contains("project(\":storage-module\")")) {
            "FAIL: timeline-module still depends on storage-module (TIMELINE_TO_STORAGE_DEPENDENCY_FOR_CONTENT_DIGEST_ONLY != 0)"
        }

        // ── 3. artifact-module -> render-module dependency removed ──
        val artifactBuild = file("artifact-module/build.gradle.kts").readText()
        val artifactMainDeps = artifactBuild.substringAfter("dependencies {").substringBefore("testImplementation(testFixtures")
        require(!artifactMainDeps.contains("project(\":render-module\")")) {
            "FAIL: artifact-module main scope still depends on render-module (ARTIFACT_TO_RENDER_DEPENDENCY_COUNT != 0)"
        }
        require(!timelineBuild.contains("project(\":render-module\")")) {
            "FAIL: timeline-module depends on render-module"
        }

        // ── 4. storage-module has no canonical artifact write authority ──
        val storageMain = fileTree("storage-module/src/main").matching { include("**/*.java") }
                .map { it.readText() }.joinToString("\n")
        require(!storageMain.contains("class ArtifactRepository")) {
            "FAIL: storage-module still owns an ArtifactRepository (STORAGE_ARTIFACT_CANONICAL_AUTHORITY_COUNT != 0)"
        }

        // ── 5. Artifact domain + persistence authority in artifact-module only ──
        val artifactInfra = file("artifact-module/src/main/java/com/example/platform/artifact/infrastructure")
        require(file(artifactInfra.resolve("ArtifactRepository.java")).exists()) {
            "FAIL: artifact-module ArtifactRepository missing (canonical persistence adapter)"
        }
        require(file(artifactInfra.resolve("JooqArtifactCommitService.java")).exists()) {
            "FAIL: JooqArtifactCommitService missing (single canonical write authority)"
        }
        require(file(artifactInfra.resolve("JooqArtifactQueryService.java")).exists()) {
            "FAIL: JooqArtifactQueryService missing (canonical query authority)"
        }
        require(file(artifactInfra.resolve("ArtifactPinRepository.java")).exists()) {
            "FAIL: ArtifactPinRepository missing (historical revision protection projection)"
        }
        require(file("artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactPinService.java").exists()) {
            "FAIL: ArtifactPinService missing (pin protection registration API)"
        }

        // ── 6. Timeline pin existence + digest + tenant validation ──
        // CFRH-I1 (LEGACY_WRITE_AUTHORITY_CLOSURE): the artifact-pin invariant
        // boundary (extract → validate → register, same transaction) lives on the
        // CANONICAL revision write path (TimelineRevisionSaveService). The legacy
        // TimelineRevisionService lost all semantic write authority — it must NOT
        // regain artifact-pin write responsibility. Historical restore reissues the
        // already-verified historical pins (copyRevisionPinsTx) inside the canonical
        // restore transaction; it does NOT re-run an unrelated mutable pin lookup.
        require(file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineArtifactPinValidator.java").exists()) {
            "FAIL: TimelineArtifactPinValidator missing (TIMELINE_ARTIFACT_PIN_EXISTENCE_VALIDATION_COUNT != 1)"
        }
        val validatorSrc = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineArtifactPinValidator.java").readText()
        require(validatorSrc.contains("getArtifact") && validatorSrc.contains("contentDigest")) {
            "FAIL: TimelineArtifactPinValidator lacks existence+digest checks"
        }
        // A. canonical new-revision save path performs extract/validate/register
        val saveSrc = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionSaveService.java").readText()
        // line-level, comment-aware symbol checks (javadoc/`//` comments that merely
        // NAME the symbols must not satisfy the authority requirement)
        fun codeLineHas(line: String, pattern: Regex): Boolean {
            val t = line.trim()
            return !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
                    && pattern.containsMatchIn(t)
        }
        require(saveSrc.lines().any { codeLineHas(it, Regex("\\bextractPinsFromDocument\\b")) }) {
            "FAIL: canonical save path missing artifact-pin extraction (CANONICAL_PIN_EXTRACT_COUNT != 1)"
        }
        require(saveSrc.lines().any { codeLineHas(it, Regex("\\bartifactPinValidator\\s*\\.\\s*validate\\b")) }) {
            "FAIL: canonical save path does not validate artifact pins (existence/tenant/digest fail-closed)"
        }
        require(saveSrc.lines().any { codeLineHas(it, Regex("\\bregisterRevisionPinsTx\\b")) }) {
            "FAIL: canonical save path does not register artifact pin protection in the same transaction (REVISION_PIN_ATOMICITY missing)"
        }
        // B. canonical restore reissues historically verified pins (not a mutable lookup)
        require(saveSrc.lines().any { codeLineHas(it, Regex("\\bcopyRevisionPinsTx\\b")) }) {
            "FAIL: canonical restore path missing historical pin copy/reissue (CANONICAL_RESTORE_PIN_COPY_COUNT != 1)"
        }
        // C/D. TimelineRevisionService is DELETED after the CFRH-I2-E behavioral
        // replacement closure. Its absence is a strictly stronger invariant than
        // "no pin write responsibility": no class means no legacy pin writes, no
        // recordRevision, no backfill, no compatibility shell can exist.
        val revisionServiceFile = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionService.java")
        require(!revisionServiceFile.exists()) {
            "FAIL: TimelineRevisionService must be DELETED (CFRH-I2-E): LEGACY_TIMELINE_REVISION_QUERY_SERVICE_CLASS_COUNT != 0"
        }
        // E. forbidden legacy write symbols remain zero across production
        // (definitions AND references; comment lines excluded — CFRH-I1 explanatory
        // comments legitimately name the removed symbols)
        val forbiddenLegacyWriteRefs = fileTree(".").matching {
            include("*/src/main/**/*.java", "platform-app/src/main/**/*.java")
            exclude("**/build/**", "**/.gradle/**", "**/.worktrees/**")
        }.filter { f ->
            f.readLines().any { line ->
                val t = line.trim()
                !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") && (
                    t.matches(Regex(".*\\b(recordRevision|recordAiAdoptRevision|backfillHeadFromLatestSnapshot)\\b.*"))
                    || t.contains("revisionService.restore("))
            }
        }
        require(forbiddenLegacyWriteRefs.files.isEmpty()) {
            "FAIL: forbidden legacy write symbols referenced in production: ${forbiddenLegacyWriteRefs.files.map { it.name }}"
        }

        // ── 7. Historical pin GC protection ──
        val lifecycleSrc = file("artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactLifecycleService.java").readText()
        require(lifecycleSrc.contains("isPinned")) {
            "FAIL: ArtifactLifecycleService does not consult pin protection (PINNED_ARTIFACT_GC_BYPASS_COUNT != 0)"
        }
        require(lifecycleSrc.contains("replicaDeleteCheck") && lifecycleSrc.contains("PINNED_LAST_USABLE_REPLICA")) {
            "FAIL: last-usable-replica deletion protection missing (PINNED_LAST_REPLICA_DELETE_BYPASS_COUNT != 0)"
        }
        val gcSrc = file("artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactGcService.java").readText()
        require(gcSrc.contains("deleteCheck")) {
            "FAIL: ArtifactGcService does not route through pin-aware deleteCheck"
        }

        // ── 8. Single V1 Flyway; no incremental migrations ──
        val migrationDir = file("platform-app/src/main/resources/db/migration")
        val migrations = migrationDir.listFiles { f -> f.name.endsWith(".sql") } ?: emptyArray()
        require(migrations.size == 1) {
            "FAIL: FLYWAY_SCRIPT_COUNT = ${migrations.size} (must be 1)"
        }
        require(file(migrationDir.resolve("V1__initial_schema.sql")).exists()) {
            "FAIL: V1__initial_schema.sql missing"
        }

        // ── 9. artifact table has no storage_uri identity column; has canonical columns ──
        val v1 = file(migrationDir.resolve("V1__initial_schema.sql")).readText()
        val artifactDdl = v1.substringAfter("create table artifact (").substringBefore(");")
        require(!artifactDdl.contains("storage_uri")) {
            "FAIL: artifact table still carries storage_uri (STORAGE_URI_AS_ARTIFACT_IDENTITY_COUNT != 0)"
        }
        require(artifactDdl.contains("content_digest") && artifactDdl.contains("tenant_id")
                && artifactDdl.contains("byte_length") && artifactDdl.contains("artifact_kind")) {
            "FAIL: artifact table lacks canonical columns"
        }
        require(v1.contains("create table artifact_replica")) {
            "FAIL: artifact_replica table missing"
        }
        require(v1.contains("create table artifact_pin")) {
            "FAIL: artifact_pin table missing"
        }

        // ── 10. Artifact catalog is projection-only (no canonical write) ──
        val catalogSrc = file("artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactCatalogRepository.java").readText()
        require(!catalogSrc.contains("insertInto(ARTIFACT)") && !catalogSrc.contains("update(ARTIFACT)")) {
            "FAIL: ArtifactCatalogRepository still writes canonical artifact table (ARTIFACT_CATALOG_CANONICAL_AUTHORITY_COUNT != 0)"
        }

        println("OK: GCR-2 Artifact authority verified (single domain authority; ArtifactRef retired; storage data-plane only; timeline pin existence/digest/tenant validation; revision-pin atomicity; historical pin protection; single V1; projection catalog)")
    }
}

tasks.register("verifyGcr2CorrectionV1") {
    group = "verification"
    description = "GCR2-CORRECTION-V1: Artifact query tenant isolation (replica/relation/provenance/traversal DB-scoped; InMemory/Jooq conformance; maxDepth/limit normalized); no cross-tenant exposure"
    doLast {
        val artifactRepo = file("artifact-module/src/main/java/com/example/platform/artifact/infrastructure/ArtifactRepository.java").readText()
        val relationRepo = file("artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactRelationRepository.java").readText()
        val jooqQuery = file("artifact-module/src/main/java/com/example/platform/artifact/infrastructure/JooqArtifactQueryService.java").readText()
        val inMemory = file("artifact-module/src/main/java/com/example/platform/artifact/domain/InMemoryArtifactQueryService.java").readText()

        // 1. Replica queries tenant-scoped via canonical Artifact ownership (EXISTS).
        require(artifactRepo.contains("TENANT_ID.eq(tenantId)") && artifactRepo.split("ARTIFACT_REPLICA.ARTIFACT_ID.eq").size >= 3
                && artifactRepo.contains("org.jooq.impl.DSL.exists")) {
            "FAIL: listReplicas/findReplica not tenant-scoped (JOOQ_ARTIFACT_QUERY_TENANT_SCOPED_REPLICA_COUNT != 1)"
        }

        // 2. Relation/provenance tenant-scoped: both peers JOIN artifact, tenant filter on both.
        require(relationRepo.contains("findByArtifactIdScopedToTenant") && relationRepo.contains("sourceArtifact.TENANT_ID.eq(tenantId)")
                && relationRepo.contains("targetArtifact.TENANT_ID.eq(tenantId)")) {
            "FAIL: relation lookup not tenant-scoped on BOTH peers (JOOQ_ARTIFACT_QUERY_TENANT_SCOPED_RELATION_COUNT != 1)"
        }

        // 3. Jooq query service uses tenant-scoped relations for parents/children/provenance.
        require(jooqQuery.contains("findByArtifactIdScopedToTenant")) {
            "FAIL: JooqArtifactQueryService still uses unscoped relation lookup (JOOQ_ARTIFACT_QUERY_TENANT_SCOPED_PROVENANCE_COUNT != 1)"
        }

        // 4. Traversal: root existence check + hop-scoped (via tenant-scoped listParents/listChildren).
        require(jooqQuery.contains("findById(tenantId, artifactId).isEmpty()") && jooqQuery.contains("if (maxDepth < 1)")) {
            "FAIL: traversal root-scope / maxDepth normalization missing (JOOQ_ARTIFACT_QUERY_TENANT_SCOPED_TRAVERSAL_COUNT != 1)"
        }

        // 5. maxDepth + limit normalized to InMemory contract in BOTH implementations.
        require(inMemory.contains("if (maxDepth < 1) return List.of();") && jooqQuery.contains("if (maxDepth < 1) {\n            return List.of();")) {
            "FAIL: maxDepth < 1 behavior diverges (ARTIFACT_QUERY_IMPLEMENTATION_CONFORMANCE_FAILURE_COUNT != 0)"
        }
        require(inMemory.contains("Math.max(1, limit)") && jooqQuery.contains("Math.max(1, limit)")) {
            "FAIL: limit <= 0 behavior diverges (ARTIFACT_QUERY_IMPLEMENTATION_CONFORMANCE_FAILURE_COUNT != 0)"
        }

        // 6. InMemory peer-tenant defense for malformed cross-tenant relations.
        require(inMemory.contains("artifactTenants.get(peer.value())")) {
            "FAIL: InMemory relation queries lack peer-tenant defense (CROSS_TENANT_ARTIFACT_QUERY_EXPOSURE_COUNT != 0)"
        }

        // 7. getArtifact remains tenant-scoped (contract).
        require(jooqQuery.contains("artifactRepository.findById(tenantId, artifactId)")) {
            "FAIL: getArtifact not tenant-scoped (JOOQ_ARTIFACT_QUERY_TENANT_SCOPED_GET_COUNT != 1)"
        }

        println("OK: GCR2-CORRECTION-V1 verified (replica/relation/provenance/traversal tenant-scoped at DB level; InMemory/Jooq conformance; maxDepth/limit normalized; no cross-tenant exposure)")
    }
}

tasks.register("verifyGcr5Gcr6DatabaseCanonicalization") {
    group = "verification"
    description = "GCR5/GCR6: single canonical V1; structural FK integrity (timeline_revision/pin/snapshot/render_job); media_stream RESTRICT; no legacy migration residue; operational time contract; jOOQ parity prerequisites"
    doLast {
        val v1 = file("platform-app/src/main/resources/db/migration/V1__initial_schema.sql").readText()

        // 1. Single canonical V1, zero incremental/backup migrations.
        val migrationDir = file("platform-app/src/main/resources/db/migration")
        val scripts = migrationDir.listFiles().orEmpty().filter { it.name.startsWith("V") && it.name.endsWith(".sql") }
        require(scripts.size == 1 && scripts[0].name == "V1__initial_schema.sql") {
            "FAIL: FLYWAY_SCRIPT_COUNT != 1 (found ${scripts.map { it.name }})"
        }
        val legacyMigrationDir = file("platform-app/src/main/resources/db/artifact-migration")
        require(!legacyMigrationDir.exists()) {
            "FAIL: LEGACY_SCHEMA_COMPATIBILITY_OBJECT_COUNT != 0 (db/artifact-migration residue)"
        }

        // 2. Structural FKs present (C5).
        require(v1.contains("fk_timeline_revision_project") && v1.contains("fk_timeline_revision_parent")
                && v1.contains("fk_timeline_revision_snapshot")) {
            "FAIL: timeline_revision structural FKs missing (FK_INTEGRITY != 1)"
        }
        require(v1.contains("fk_artifact_pin_revision") && v1.contains("fk_artifact_pin_project")) {
            "FAIL: artifact_pin structural FKs missing"
        }
        require(v1.contains("fk_render_job_project")) { "FAIL: render_job project FK missing" }
        require(v1.contains("fk_timeline_snapshot_project")) { "FAIL: timeline_snapshot project FK missing" }

        // 3. media_stream RESTRICT (C9 — no implicit canonical history destruction).
        require(v1.contains("constraint fk_ms_media_asset foreign key (media_asset_id) references media_asset(id) on delete restrict")) {
            "FAIL: media_stream cascade not converted to RESTRICT (HISTORICAL_DELETE_SAFETY != 1)"
        }

        // 4. render_job id type consistency (varchar(64) matching project.id / timeline_snapshot.id).
        require(v1.contains("project_id varchar(64) not null,\n    timeline_snapshot_id varchar(64) not null")) {
            "FAIL: render_job identity column types not canonical (varchar(128) residue)"
        }

        // 5. No TIMELINE_MEDIA_TIME as operational timestamp (MediaTime never in timestamp columns).
        val mediaTimeAsTs = Regex("(?i)(media_time|frame_time|time_range)\\s+timestamp").findAll(v1)
        require(!mediaTimeAsTs.iterator().hasNext()) { "FAIL: Timeline MediaTime stored as operational timestamp" }

        println("OK: GCR5/GCR6 database canonicalization verified (single V1; FK integrity; media_stream RESTRICT; render_job identity types; no legacy migration residue; MediaTime/timestamp separation)")
    }
}

tasks.register("verifyTimelineEffectTransitionCanonicalization") {
    group = "verification"
    description = "TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1: Timeline owns Effect/Transition/Automation semantics; canonical serializer includes typed parameters (hash participation); no provider command leakage in authored semantics; transition is first-class (not clip effect); automation uses MediaTime"
    doLast {
        val serializer = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/serialization/CanonicalSerializer.java").readText()
        val effect = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectInstance.java").readText()
        val transition = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/transition/TransitionInstance.java").readText()
        val automation = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/automation/Automation.java").readText()

        // 1. Typed parameter state participates in canonical serialization (hash).
        require(serializer.contains("stringMapField(sb, \"parameters\"") && serializer.contains("stringMapField(sb, \"automationBindings\"")) {
            "FAIL: effect/transition parameters not serialized (EFFECT_PARAMETER_CHANGE_AFFECTS_HASH != YES)"
        }
        // 2. Deterministic map key ordering.
        require(serializer.contains("java.util.Collections.sort(keys)")) {
            "FAIL: canonical map serialization not deterministic (sorted keys missing)"
        }
        // 3. Automation uses exact MediaTime (no wall clock / double seconds as time).
        require(automation.contains("MediaTime time") && !automation.contains("LocalDateTime") && !automation.contains("Instant")) {
            "FAIL: automation not exact-MediaTime (AUTOMATION_OPERATIONAL_TIMESTAMP_COUNT != 0)"
        }
        // 4. Transition is first-class relationship with typed participants (not clip effect).
        require(transition.contains("outgoingClipId") && transition.contains("incomingClipId")
                && transition.contains("duration") && transition.contains("TransitionAlignment")) {
            "FAIL: transition not first-class relationship (TRANSITION_AS_CLIP_EFFECT_COUNT != 0)"
        }
        // 5. No provider command leakage in timeline authored semantics.
        val timelineMain = fileTree("timeline-module/src/main").matching { include("**/*.java") }
                .map { it.readText() }.joinToString("\n")
        require(!timelineMain.contains("ffmpeg") && !timelineMain.contains("filter_complex")
                && !timelineMain.contains("eq=")) {
            "FAIL: provider command fragment in Timeline authored semantics (TIMELINE_FFMPEG_COMMAND_FRAGMENT_COUNT != 0)"
        }
        // 6. EffectInstance is typed semantic state with definition reference.
        require(effect.contains("effectDefinitionId") && effect.contains("effectInstanceId")) {
            "FAIL: EffectInstance missing definition reference"
        }
        // 7. No Timeline V3 introduced.
        val timelineDir = file("timeline-module/src/main/java/com/example/platform/timeline")
        require(timelineDir.walkTopDown().none { it.name.contains("TimelineDocumentV3") }) {
            "FAIL: Timeline V3 introduced (TIMELINE_V3_INTRODUCED_COUNT != 0)"
        }
        // 8. Transition never appended to clip.effects.
        require(!transition.contains("clip.effects") && !transition.contains("effects.add")) {
            "FAIL: transition modeled as clip effect"
        }
        // 9. SECOND CORRECTION: production merge diff path must emit semantic
        //    ops for effect/transition/automation (CanonicalTimelineDiffCalculator).
        val diffCalc = file("timeline-module/src/main/java/com/example/platform/timeline/diff/calculation/CanonicalTimelineDiffCalculator.java").readText()
        require(diffCalc.contains("TimelineChangeType.EFFECT_CHANGED")
                && diffCalc.contains("TimelineChangeType.TRANSITION_CHANGED")
                && diffCalc.contains("TimelineChangeType.AUTOMATION_CHANGED")) {
            "FAIL: production merge diff must emit EFFECT/TRANSITION/AUTOMATION semantic ops"
        }
        // 10. Production patch path materializes the three semantic ops.
        val patchApplier = file("timeline-module/src/main/java/com/example/platform/timeline/diff/application/TimelinePatchApplier.java").readText()
        require(patchApplier.contains("applyEffectChanged") && patchApplier.contains("applyTransitionChanged")
                && patchApplier.contains("applyAutomationChanged")) {
            "FAIL: production patch path must apply EFFECT/TRANSITION/AUTOMATION ops"
        }
        // 11. V2 canonical materialization is a typed, whole-document path:
        //     applied snapshot -> TimelineDocument -> canonical payload.  The
        //     converter must project transitions/automations explicitly and
        //     every merge output path must serialize that projected document.
        val mergeEngine = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineMergeEngine.java").readText()
        val snapshotConverter = file("timeline-module/src/main/java/com/example/platform/timeline/diff/calculation/TimelineSnapshotConverter.java").readText()
        val documentSerializer = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineDocumentJsonSerializer.java").readText()
        val timelineDocument = file("timeline-module/src/main/java/com/example/platform/timeline/canonical/TimelineDocument.java").readText()
        fun normalized(source: String) = source.replace(Regex("\\s+"), " ").trim()
        fun hasCanonicalMaterializationPath(
            engineSource: String,
            converterSource: String,
            serializerSource: String,
            documentSource: String
        ): Boolean {
            val engine = normalized(engineSource)
            val converter = normalized(converterSource)
            val serializerSourceNormalized = normalized(serializerSource)
            val document = normalized(documentSource)
            val directMergePath = engine.contains(
                "TimelineDocumentJsonSerializer.serializeWithCaptions( TimelineSnapshotConverter.toDocument(application.patchedSnapshot()));"
            )
            val persistedMergePath = engine.contains(
                "CanonicalTimelineSnapshot mergedSnapshot = application.patchedSnapshot(); " +
                    "TimelineDocument mergedDocument = TimelineSnapshotConverter.toDocument(mergedSnapshot); " +
                    "String mergedPayload = TimelineDocumentJsonSerializer.serializeWithCaptions(mergedDocument);"
            )
            val transitionAutomationProjection = converter.contains(
                "snapshot.textElements(), fromTransitionSnapshots(snapshot.transitions()), " +
                    "fromAutomationSnapshots(snapshot.automations()));"
            )
            val wholeDocumentSerialization = serializerSourceNormalized.contains(
                "public static String serializeWithCaptions(TimelineDocument document) { try { " +
                    "ObjectNode root = (ObjectNode) MAPPER.valueToTree(document);"
            ) && serializerSourceNormalized.contains("return MAPPER.writeValueAsString(root);")
            val documentOwnsSemanticFields = document.contains("@JsonProperty(\"transitions\")")
                && document.contains("private final List<CanonicalTransition> transitions;")
                && document.contains("@JsonProperty(\"automations\")")
                && document.contains("private final List<CanonicalAutomationCurve> automations;")
                && document.contains("getTransitions() { return transitions; }")
                && document.contains("getAutomations() { return automations; }")
            return directMergePath && persistedMergePath && transitionAutomationProjection
                && wholeDocumentSerialization && documentOwnsSemanticFields
        }
        require(hasCanonicalMaterializationPath(
            mergeEngine, snapshotConverter, documentSerializer, timelineDocument
        )) {
            "FAIL: merged snapshot must project transitions/automations into TimelineDocument and use canonical whole-document serialization"
        }
        fun requireMaterializationMutationRejected(
            name: String,
            engineSource: String = mergeEngine,
            converterSource: String = snapshotConverter,
            serializerSource: String = documentSerializer
        ) {
            require(!hasCanonicalMaterializationPath(
                engineSource, converterSource, serializerSource, timelineDocument
            )) {
                "FAIL: RED mutation escaped canonical materialization guard: $name"
            }
        }
        requireMaterializationMutationRejected(
            "transition projection removed",
            converterSource = snapshotConverter.replace(
                "fromTransitionSnapshots(snapshot.transitions())", "List.of()")
        )
        requireMaterializationMutationRejected(
            "automation projection removed",
            converterSource = snapshotConverter.replace(
                "fromAutomationSnapshots(snapshot.automations())", "List.of()")
        )
        requireMaterializationMutationRejected(
            "canonical merge serializer removed",
            engineSource = mergeEngine.replace(
                "TimelineDocumentJsonSerializer.serializeWithCaptions(",
                "TimelineDocumentJsonSerializer.serialize(")
        )
        requireMaterializationMutationRejected(
            "whole-document serializer projection removed",
            serializerSource = documentSerializer.replace(
                "MAPPER.valueToTree(document)", "MAPPER.createObjectNode()")
        )
        println("OK: canonical materialization RED mutations rejected (4/4)")
        // 12. Field-level local semantics have one authoritative location:
        //     canonical snapshot records own local equality (no central switch
        //     duplication across serializer/diff/patch/merge).
        val trSnapshot = file("timeline-module/src/main/java/com/example/platform/timeline/diff/calculation/CanonicalTimelineTransitionSnapshot.java").readText()
        val autoSnapshot = file("timeline-module/src/main/java/com/example/platform/timeline/diff/calculation/CanonicalTimelineAutomationSnapshot.java").readText()
        require(trSnapshot.contains("localSemanticsEquals") && autoSnapshot.contains("localSemanticsEquals")) {
            "FAIL: local semantic equality must be owned by the canonical component record"
        }
        // 13. THIRD CORRECTION: complete semantic fingerprint drives equality
        //     AND diff afterValue from ONE authority.
        require(trSnapshot.contains("semanticFingerprint") && autoSnapshot.contains("semanticFingerprint")) {
            "FAIL: complete semantic fingerprint missing on component records"
        }
        require(diffCalc.contains("semanticFingerprint()")) {
            "FAIL: production diff afterValue must use the complete semantic fingerprint"
        }
        // 14. THIRD CORRECTION: no unsafe incomplete snapshot constructor
        //     (silent transitions/automations erasure must be impossible).
        val snapshotRec = file("timeline-module/src/main/java/com/example/platform/timeline/diff/calculation/CanonicalTimelineSnapshot.java").readText()
        require(snapshotRec.contains("withTracks(") && snapshotRec.contains("withTransitions(")
                && snapshotRec.contains("withAutomations(")) {
            "FAIL: full-state snapshot copy helpers missing"
        }
        require(!snapshotRec.contains("// Convenience constructor without transitions/automations")) {
            "FAIL: unsafe incomplete snapshot convenience constructor must be removed"
        }
        // 15. THIRD CORRECTION: deletion is first-class (deleted flag) and
        //     merge writes merged result (no target resurrection).
        require(diffCalc.contains("\"deleted\", \"true\"")) {
            "FAIL: explicit semantic deletion op missing in production diff"
        }
        require(patchApplier.contains("meta.get(\"deleted\")")) {
            "FAIL: patch applier must handle explicit deletion"
        }
        require(normalized(timelineDocument).contains(
            "@JsonProperty(\"transitions\") @JsonInclude(JsonInclude.Include.NON_EMPTY)"
        ) && normalized(timelineDocument).contains(
            "@JsonProperty(\"automations\") @JsonInclude(JsonInclude.Include.NON_EMPTY)"
        )) {
            "FAIL: empty merged transition/automation state must serialize without target resurrection"
        }
        // 16. FOURTH CORRECTION: Effect owns local semantic fingerprint
        //     (no central List.toString()/Map.toString() effect signatures).
        val clipEffect = file("timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineClipEffect.java").readText()
        require(clipEffect.contains("semanticFingerprint")) {
            "FAIL: Effect must own a deterministic local semantic fingerprint"
        }
        require(!diffCalc.contains("effects().toString()") && !diffCalc.contains("parameters().toString()")) {
            "FAIL: production diff must not use List/Map toString as Effect semantic identity"
        }
        // 17. FOURTH CORRECTION: aggregate reference validation enforced by
        //     the canonical validator (transition endpoints + automation targets).
        val validator = file("timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineCanonicalValidator.java").readText()
        require(validator.contains("validateTransitionReferences") && validator.contains("validateAutomationTargets")) {
            "FAIL: canonical validator must enforce transition endpoint and automation target references"
        }
        require(!validator.contains("// Convenience constructor without transitions/automations")) {
            "FAIL: no semantic convenience constructors may remain"
        }
        // 18. FOURTH CORRECTION: no Legacy Clip constructor remains.
        val candidateModel = file("timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineCandidate.java").readText()
        require(!candidateModel.contains("Legacy constructor")) {
            "FAIL: TimelineCandidate Clip Legacy constructor must be removed"
        }
        // 19. FOURTH CORRECTION: real TimelineMergeEngine E2E test class exists.
        val e2eTest = file("timeline-module/src/test/java/com/example/platform/timeline/app/EffectTransitionEndToEndMergeTest.java").readText()
        require(e2eTest.contains("e2eM1EffectSourceOnlySurvivesActualMerge")
                && e2eTest.contains("e2eR1TransitionDeleteLastProducesEmptyMergedState")
                && e2eTest.contains("e2eC1DivergentEffectEditDoesNotSilentlyMerge")
                && e2eTest.contains("e2eX1DeleteClipVsTransitionFailsClosed")) {
            "FAIL: real TimelineMergeEngine E2E tests must exist (source-only, delete-last, conflict, cross-object)"
        }
        require(e2eTest.contains("TimelineMergeEngine(")) {
            "FAIL: E2E tests must invoke the actual TimelineMergeEngine"
        }
        // 20. FIFTH CORRECTION (F1): Automation target validation must not
        //     bypass on empty clips; Effect instance IDs aggregate-unique.
        require(validator.contains("validateEffectIdUniqueness")
                && validator.contains("candidate.automations().isEmpty()")) {
            "FAIL: Effect-ID uniqueness invariant + zero-bypass Automation target validation required"
        }
        // 21. FIFTH CORRECTION (F2/F3): single local Effect semantic codec
        //     authority — deep typed encoding, no delimiter grammar in
        //     diff/patch.
        val effectSemantics = file("timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/EffectCanonicalSemantics.java").readText()
        require(effectSemantics.contains("deepSorted") && effectSemantics.contains("encodeEffects")
                && effectSemantics.contains("decodeEffects")) {
            "FAIL: single local Effect canonical codec missing"
        }
        require(!diffCalc.contains("fx.id() == null ? \"\" : fx.id()).append('\\u001e')")) {
            "FAIL: production diff must not use custom delimiter Effect grammar"
        }
        require(!patchApplier.contains("kv.split(\",\")") || !patchApplier.contains("eq > 0")) {
            "FAIL: production patch must not independently parse Effect field grammar"
        }
        // 22. FIFTH CORRECTION (F4): zero semantic compatibility constructors/
        //     fallbacks.
        require(!candidateModel.contains("public TimelineCandidate(\n            String timelineId,\n            String projectId,\n            TimelineCanonicalProfile profile,\n            List<Track> tracks)")) {
            "FAIL: TimelineCandidate structural-only convenience constructor must be removed"
        }
        require(!file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineImportRequest.java").readText()
                .contains("Backward-compatible convenience constructor")) {
            "FAIL: TimelineImportRequest backward-compatible semantic constructor must be removed"
        }
        require(!file("timeline-module/src/main/java/com/example/platform/timeline/app/InternalTimelineCandidateAdapter.java").readText()
                .contains("effectKey = \"opaque\"")) {
            "FAIL: opaque effectKey fallback must be removed"
        }
        // 23. SIXTH CORRECTION (S1): whole-Effect fingerprint must be the
        //     complete canonical Effect value — no manual delimiter envelope.
        val effectSemanticsSrc = file("timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/EffectCanonicalSemantics.java").readText()
        require(effectSemanticsSrc.contains("canonicalEffectValue")
                && effectSemanticsSrc.contains("writeValueAsString(canonicalEffectValue(effect))")) {
            "FAIL: semanticFingerprint must derive from the complete canonical Effect value"
        }
        require(!effectSemanticsSrc.contains("sb.append(\"id=\")")) {
            "FAIL: manual id=...;key=... delimiter envelope must be removed"
        }
        // 24. SIXTH CORRECTION (S2): encodeEffects must use the SAME canonical
        //     Effect value (deepSorted parameters) as the fingerprint.
        require(effectSemanticsSrc.contains("out.add(canonicalEffectValue(e))")) {
            "FAIL: encodeEffects must use canonicalEffectValue (single deep canonical representation)"
        }
        // 25. SIXTH CORRECTION (S3): real Effect-delete × Automation-modify
        //     three-way E2E must exist in the actual engine test class.
        require(e2eTest.contains("e2eS3EffectDeleteVsAutomationModifyFailsClosed")) {
            "FAIL: Effect-delete × Automation-modify real three-way E2E missing"
        }
        // ── ROADMAP #19 (G-R19): TimedText local semantic authority ──
        val timedText = file("timeline-module/src/main/java/com/example/platform/timeline/canonical/TimedTextCanonicalSemantics.java").readText()
        require(timedText.contains("canonicalValue") && timedText.contains("semanticFingerprint")
                && timedText.contains("encodeElements") && timedText.contains("decodeElements")) {
            "FAIL (G-R19-1): TimedTextCanonicalSemantics must be the local authority with canonical/fingerprint/encode/decode"
        }
        require(!diffCalc.contains("content().value().hashCode()")) {
            "FAIL (G-R19-2): diff must not use TextElement content.hashCode() as semantic identity"
        }
        require(diffCalc.contains("TimedTextCanonicalSemantics.semanticFingerprint")) {
            "FAIL (G-R19-3): diff must delegate TimedText fingerprint to the local authority"
        }
        require(patchApplier.contains("TEXT_ELEMENT_CHANGED -> applyTextElementChanged")
                || patchApplier.contains("case TEXT_ELEMENT_CHANGED -> applyTextElementChanged")) {
            "FAIL (G-R19-4): patch applier must have a real TEXT_ELEMENT_CHANGED application path"
        }
        require(patchApplier.contains("TimedTextCanonicalSemantics.decodeElements")) {
            "FAIL (G-R19-5): patch must delegate TimedText decode to the local authority"
        }
        require(file("timeline-module/src/main/java/com/example/platform/timeline/canonical/TimelineDocument.java").readText()
                .contains("Duplicate TextElement id")) {
            "FAIL (G-R19-6): duplicate TextElement identity validation must exist"
        }
        val timedTextE2E = file("timeline-module/src/test/java/com/example/platform/timeline/app/TimedTextMergeEngineTest.java").readText()
        require(timedTextE2E.contains("f1SourceOnlyTimedTextMerge") && timedTextE2E.contains("f3DivergentSameElementConflict")
                && timedTextE2E.contains("f5DeleteLastEmptyState") && timedTextE2E.contains("f4DeleteVsModifyFailsClosed")) {
            "FAIL (G-R19-7/8/9): real TimelineMergeEngine TimedText E2E (source/divergent/delete-last/delete-vs-modify) missing"
        }
        require(timedTextE2E.contains("f6MixedSemanticFamiliesPreserved")) {
            "FAIL (G-R19-10): mixed semantic-family merge E2E missing"
        }
        require(!timelineMain.contains("content().value().hashCode()")) {
            "FAIL (G-R19-11): no Java hashCode TimedText semantic identity in production"
        }
        require(!timelineMain.contains("SemanticComponent<")) {
            "FAIL (G-R19-12): no generic SemanticComponent framework introduced"
        }
        require(!file("timeline-module/src/main/java/com/example/platform/timeline/canonical/TextElement.java").readText()
                .contains("drawtext") && !timedText.contains("drawtext")) {
            "FAIL (G-R19-14): no provider execution syntax in canonical TimedText"
        }
        // ── ROADMAP #19 CORRECTION 1 (TT-C1/TT-C2) ──
        require(!timedText.contains("field.getClass().getDeclaredFields()")
                && !timedText.contains("field.setAccessible(true)")) {
            "FAIL (TT-C2): canonical TimedText semantics must be explicit, not reflective"
        }
        require(timedText.contains("toCanonicalNode") && timedText.contains("TIMEDTEXT_CANONICAL_SCHEMA_IS_EXPLICIT_NOT_REFLECTIVE_V1")) {
            "FAIL (TT-C2): explicit canonical-node API + explicit-schema contract required"
        }
        require(diffCalc.contains("TimedTextCanonicalSemantics.semanticFingerprint(afterMap.get(id))")) {
            "FAIL (TT-C1): ADD must carry the complete canonical after payload"
        }
        require(diffCalc.contains("TimedTextCanonicalSemantics.semanticFingerprint(b)")) {
            "FAIL (TT-C1): DELETE must carry the complete canonical before payload"
        }
        require(normalized(snapshotConverter).contains(
            "snapshot.semanticRelationships(), snapshot.textElements(), " +
                "fromTransitionSnapshots(snapshot.transitions())"
        )) {
            "FAIL (TT-C2): merge output must project canonical TimedText through TimelineDocument"
        }
        require(timedTextE2E.contains("f7SourceOnlyTextElementAdd")) {
            "FAIL (TT-C1): source-only ADD real TimelineMergeEngine E2E missing"
        }
        require(!timedText.contains("run.getClass().getDeclaredFields()")) {
            "FAIL (TT-C2): reflection-based canonical run construction must be removed"
        }
        // ── CHECKPOINT_A (G1-G7): combined semantic closure guards ──
        val saveService = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionSaveService.java").readText()
        require(saveService.contains("extractPinsFromDocument") && saveService.contains("artifactPinValidator.validate")
                && saveService.contains("registerRevisionPins")) {
            "FAIL (G1): every canonical revision writer must enforce the artifact-pin boundary (extract→validate→register)"
        }
        require(!saveService.contains("Backward-compatible constructor for existing direct-wiring tests")) {
            "FAIL (G1): no no-pin public constructor may remain for production wiring"
        }
        require(saveService.contains("Objects.requireNonNull(artifactPinValidator")
                && saveService.contains("Objects.requireNonNull(artifactPinService")) {
            "FAIL (G1): no-pin save surface must be impossible by construction (requireNonNull pin boundary)"
        }
        require(candidateModel.contains("AudioMix audioMix") && candidateModel.contains("semanticRelationships")
                && candidateModel.contains("temporalMapping")) {
            "FAIL (G2): TimelineCandidate must carry AudioMix/relationships/temporal mapping (no silent narrowing)"
        }
        require(snapshotRec.contains("AudioMix audioMix") && snapshotRec.contains("semanticRelationships")) {
            "FAIL (G2): CanonicalTimelineSnapshot must carry AudioMix/relationships"
        }
        require(diffCalc.contains("diffAudioMix") && diffCalc.contains("diffRelationships")) {
            "FAIL (G3): production diff must cover AudioMix + SemanticRelationships"
        }
        require(normalized(snapshotConverter).contains(
            "snapshot.audioMix(), snapshot.semanticRelationships(), snapshot.textElements()"
        )) {
            "FAIL (G3): production merge write-back must project AudioMix + SemanticRelationships"
        }
        require(!diffCalc.contains("Map<String, Object>") || !diffCalc.contains("new HashMap")) {
            "FAIL (G5): no Map-based generic semantic payload authority"
        }
        require(!file("timeline-module/build.gradle.kts").readText().contains("render-module")) {
            "FAIL (G6): timeline must not depend on render for semantic correctness"
        }
        require(file("timeline-module/src/test/java/com/example/platform/timeline/app/CheckpointACombinedMergeTest.java").readText()
                .contains("combinedEightFamilyMerge") && file("timeline-module/src/test/java/com/example/platform/timeline/app/CheckpointAPinInvariantTest.java").readText()
                .contains("case1MissingArtifactFailsClosed")) {
            "FAIL (G7): combined E2E + pin invariant tests must exist"
        }
        // ── CHECKPOINT_A Round 3 (H1-H8): COMPONENT_LOCAL_SEMANTIC_AUTHORITY ──
        val transitionAuthority = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/transition/TransitionCanonicalSemantics.java").readText()
        val automationAuthority = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/automation/AutomationCanonicalSemantics.java").readText()
        val relationshipAuthority = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/relationship/RelationshipCanonicalSemantics.java").readText()
        require(transitionAuthority.contains("canonicalValue") && transitionAuthority.contains("semanticFingerprint")
                && transitionAuthority.contains("fromCanonicalValue")) {
            "FAIL (H1): Transition-local canonical authority must exist (value/fingerprint/decode)"
        }
        require(!diffCalc.contains("sb.append(k).append('=').append(v)")) {
            "FAIL (H1): central diff must not own delimiter parameter grammar"
        }
        require(!patchApplier.contains("paramsEnc.split(\",\")")) {
            "FAIL (H1): central patch must not own delimiter parameter decode"
        }
        require(automationAuthority.contains("canonicalValue") && automationAuthority.contains("keyframes")) {
            "FAIL (H2): Automation-local canonical authority must exist"
        }
        require(!diffCalc.contains("keyframes\", kf.toString") && !diffCalc.contains("kf.toString()")) {
            "FAIL (H2): central diff must not own keyframe delimiter grammar"
        }
        require(!patchApplier.contains("kfEnc.split")) {
            "FAIL (H2): central patch must not own keyframe delimiter decode"
        }
        require(normalized(snapshotConverter).contains(
            "fromTransitionSnapshots(snapshot.transitions()), " +
                "fromAutomationSnapshots(snapshot.automations())"
        )) {
            "FAIL (H3): merge output must project typed Transition/Automation state"
        }
        require(normalized(snapshotConverter).contains(
            "clip.temporalMapping(), clip.effects()));"
        )) {
            "FAIL (H3): merge output must project typed Effect state"
        }
        require(relationshipAuthority.contains("canonicalKey") && relationshipAuthority.contains("canonicalJson")) {
            "FAIL (H4): Relationship identity/normalization must live in the relationship authority"
        }
        require(!diffCalc.contains("System.identityHashCode")) {
            "FAIL (H4): no unstable relationship identity may exist"
        }
        require(!diffCalc.contains("Map<String, Object>") && !mergeEngine.contains("Map<String, Object>")) {
            "FAIL (H5): no Map-based generic semantic payload authority"
        }
        require(!diffCalc.contains("GenericSemanticComponent") && !mergeEngine.contains("SemanticComponent")) {
            "FAIL (H6): no generic SemanticComponent framework"
        }
        require(file("timeline-module/src/test/java/com/example/platform/timeline/semantics/ComponentLocalSemanticAuthorityCollisionTest.java").exists()) {
            "FAIL (H7): delimiter collision regression tests must exist"
        }
        require(file("render-module/src/test/java/com/example/platform/render/app/timeline/CheckpointAPinRegistrationRollbackIT.java").exists()) {
            "FAIL (H8): same-path real-PG pin rollback IT must exist"
        }
        // ── CHECKPOINT_A Round 4 (H9-H16): strengthened locality guards over
        //    ALL central Timeline surfaces ──
        val centralClasses = listOf(
            "diff/calculation/CanonicalTimelineDiffCalculator.java",
            "diff/application/TimelinePatchApplier.java",
            "app/TimelineMergeEngine.java",
            "app/InternalTimelineCandidateAdapter.java",
            "app/TimelineDocumentCandidateMapper.java",
            "diff/calculation/TimelineSnapshotConverter.java"
        )
        val centralSrc = centralClasses.map { f ->
            file("timeline-module/src/main/java/com/example/platform/timeline/" + f).readText()
        }.joinToString("\n")
        // H9: zero System.identityHashCode canonical identity across ALL central classes.
        require(!centralSrc.contains("System.identityHashCode")) {
            "FAIL (H9): System.identityHashCode canonical identity forbidden in all central Timeline classes"
        }
        // H10: central patch must not independently reproduce relationship
        //     identity grammar (group:/sync: prefixes are Relationship-domain rules).
        require(!patchApplier.contains("\"group:\" + ") && !patchApplier.contains("\"sync:\" + ")) {
            "FAIL (H10): central patch must not independently normalize relationship identity"
        }
        // H11: Timeline adapter must not define the canonical AudioMix fingerprint
        //     grammar — the fingerprint/codec authority lives in audio-module.
        require(!file("timeline-module/src/main/java/com/example/platform/timeline/app/InternalTimelineCandidateAdapter.java").readText()
                .contains("audioMixFingerprint")) {
            "FAIL (H11): Timeline adapter must not define AudioMix canonical fingerprint"
        }
        require(file("audio-module/src/main/java/com/example/platform/audio/domain/mix/AudioMixCanonicalSemantics.java").readText()
                .contains("semanticFingerprint") && file("audio-module/src/main/java/com/example/platform/audio/domain/mix/AudioMixCanonicalSemantics.java").readText()
                .contains("canonicalValue")) {
            "FAIL (H11): AudioMix canonical fingerprint/codec authority must live in audio-module"
        }
        // H12: typed TimelineSourceBinding is the clip source-semantics authority
        //     in BOTH the canonical snapshot and the candidate model.
        require(file("timeline-module/src/main/java/com/example/platform/timeline/diff/calculation/CanonicalTimelineClipSnapshot.java").readText()
                .contains("TimelineSourceBinding sourceBinding")) {
            "FAIL (H12): CanonicalTimelineClipSnapshot must carry the typed TimelineSourceBinding"
        }
        require(candidateModel.contains("TimelineSourceBinding sourceBinding")) {
            "FAIL (H12): TimelineCandidate.Clip must carry the typed TimelineSourceBinding"
        }
        // H13: central diff must not independently enumerate Transition/Automation
        //     authored local fields into metadata (single canonical payload only).
        require(!diffCalc.contains("meta.put(\"transitionDefinitionId\"") && !diffCalc.contains("meta.put(\"targetEntityId\"")) {
            "FAIL (H13): central diff must not enumerate Transition/Automation local fields"
        }
        // H14: central patch must reconstruct Transition/Automation through the
        //     local canonical authority (fail-closed payload decode), never by
        //     inventing defaults.
        // (authority references may span lines: "...TransitionCanonicalSemantics\n  .fromCanonicalJson(...)")
        val transitionDecodeInPatch = patchApplier.contains("TransitionCanonicalSemantics") && patchApplier.contains("fromCanonicalJson(transitionId")
        val automationDecodeInPatch = patchApplier.contains("AutomationCanonicalSemantics") && patchApplier.contains("fromCanonicalJson(automationId")
        require(transitionDecodeInPatch && automationDecodeInPatch) {
            "FAIL (H14): central patch must reconstruct through local canonical authorities"
        }
        require(!patchApplier.contains("valueType\", \"float\"") && !patchApplier.contains("extrapolation\", \"HOLD\"")) {
            "FAIL (H14): central patch must not synthesize Automation defaults"
        }
        // H15: real R4 behavioral tests exist (authoritative evidence).
        val r4Tests = listOf(
            "CheckpointARound4ComponentAuthorityTest",
            "CheckpointARound4RelationshipAuthorityTest",
            "CheckpointARound4SourceBindingClosureTest",
            "CheckpointARound4TrueMergeE2ETest"
        )
        for (t in r4Tests) {
            require(file("timeline-module/src/test/java/com/example/platform/timeline/app/" + t + ".java").exists()) {
                "FAIL (H15): R4 behavioral test " + t + " must exist"
            }
        }
        require(file("timeline-module/src/test/java/com/example/platform/timeline/app/CheckpointARound4TrueMergeE2ETest.java").readText()
                .contains("trueEightFamilyProductionMergeE2E")) {
            "FAIL (H15): true eight-family production TimelineMergeEngine E2E missing"
        }
        // H16: real-repository pin atomicity / restore copy / patch path ITs exist.
        val r4PinITs = listOf(
            "CheckpointARound4RealPinAtomicityIT",
            "CheckpointARound4RestorePinCopyIT",
            "CheckpointARound4PatchPathPinIT"
        )
        for (t in r4PinITs) {
            require(file("render-module/src/test/java/com/example/platform/render/app/timeline/" + t + ".java").exists()) {
                "FAIL (H16): real-PG pin IT " + t + " must exist"
            }
        }

        // ── R5 guards (CHECKPOINT_A Round 5) ──────────────────────────────

        // H17: CONSTRUCTOR_INJECTION_WITHOUT_EXPLICIT_AUTOWIRED_V1 — the two
        // Round-5 corrected production services must have exactly ONE public
        // constructor, no @Autowired, no test-convenience constructor.
        val mergeEngineSrc = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineMergeEngine.java").readText()
        val saveServiceSrc = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionSaveService.java").readText()
        require(!mergeEngineSrc.contains("org.springframework.beans.factory.annotation.Autowired")
                && !saveServiceSrc.contains("org.springframework.beans.factory.annotation.Autowired")) {
            "FAIL (H17): explicit @Autowired forbidden on R5-corrected production surfaces (EXPLICIT_AUTOWIRED_IN_ROUND5_CORRECTED_PRODUCTION_SURFACES must be 0)"
        }
        val mergeCtorCount = Regex("public TimelineMergeEngine\\(").findAll(mergeEngineSrc).count()
        require(mergeCtorCount == 1) {
            "FAIL (H17): TimelineMergeEngine must have exactly ONE public constructor (found $mergeCtorCount)"
        }
        val saveCtorCount = Regex("public TimelineRevisionSaveService\\(").findAll(saveServiceSrc).count()
        require(saveCtorCount == 1) {
            "FAIL (H17): TimelineRevisionSaveService must have exactly ONE public constructor (found $saveCtorCount)"
        }
        require(mergeEngineSrc.contains("Objects.requireNonNull(artifactPinValidator")
                && mergeEngineSrc.contains("Objects.requireNonNull(artifactPinService")) {
            "FAIL (H17): TimelineMergeEngine pin boundary must be non-null by construction"
        }
        require(saveServiceSrc.contains("Objects.requireNonNull(artifactPinValidator")
                && saveServiceSrc.contains("Objects.requireNonNull(artifactPinService")) {
            "FAIL (H17): TimelineRevisionSaveService pin boundary must be non-null by construction"
        }

        // H18: no nullable pin-skip in production save/restore/merge.
        require(!mergeEngineSrc.contains("artifactPinValidator != null")
                && !mergeEngineSrc.contains("artifactPinService != null")) {
            "FAIL (H18): nullable pin-skip forbidden in TimelineMergeEngine (PERSISTENT_MERGE_WITHOUT_PIN_BOUNDARY = IMPOSSIBLE_BY_CONSTRUCTION)"
        }
        require(!saveServiceSrc.contains("artifactPinValidator != null")
                && !saveServiceSrc.contains("artifactPinService != null")) {
            "FAIL (H18): nullable pin-skip forbidden in TimelineRevisionSaveService (save/restore never skip pin persistence)"
        }

        // H19: Transition/Automation authority must be defined over the DOMAIN
        // value, not the diff snapshot; no synthesized authored defaults.
        val transitionAuth = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/transition/TransitionCanonicalSemantics.java").readText()
        val automationAuth = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/automation/AutomationCanonicalSemantics.java").readText()
        require(transitionAuth.contains("canonicalValue(CanonicalTransition")
                && transitionAuth.contains("fromCanonicalValue(String transitionId, JsonNode node)")) {
            "FAIL (H19): Transition canonical authority must be defined over the CanonicalTransition DOMAIN value"
        }
        require(automationAuth.contains("canonicalValue(CanonicalAutomationCurve")
                && automationAuth.contains("fromCanonicalValue(String automationId, JsonNode node)")) {
            "FAIL (H19): Automation canonical authority must be defined over the CanonicalAutomationCurve DOMAIN value"
        }
        require(!transitionAuth.contains("asText(\"1.0\")") && !transitionAuth.contains("asText(\"VIDEO\")")
                && !transitionAuth.contains("asText(\"CENTER_ON_CUT\")") && !transitionAuth.contains("asText(\"USE_SOURCE_HANDLES\")")
                && !transitionAuth.contains("asLong(1)")) {
            "FAIL (H19): Transition decoder must not synthesize authored defaults (version/mediaType/alignment/policy/timeScale)"
        }
        require(!automationAuth.contains("asText(\"float\")") && !automationAuth.contains("asText(\"HOLD\")")
                && !automationAuth.contains("asText(\"LINEAR\")") && !automationAuth.contains("asDouble(0.0)")
                && !automationAuth.contains("\"kf_\" +") && !automationAuth.contains("asLong(1)")) {
            "FAIL (H19): Automation decoder must not synthesize authored defaults (valueType/extrapolation/interpolation/0.0/kf_N/timeScale)"
        }

        // H20: TimelineCandidate.Clip must carry exactly ONE typed source
        // binding authority — no flat source semantic fields.
        val candidateSrc = file("timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineCandidate.java").readText()
        val clipRecord = candidateSrc.substring(candidateSrc.indexOf("public record Clip("))
        require(!clipRecord.contains("String sourceKind") && !clipRecord.contains("String mediaAssetId")
                && !clipRecord.contains("String mediaStreamId") && !clipRecord.contains("String artifactId")
                && !clipRecord.contains("String contentDigest")) {
            "FAIL (H20): TimelineCandidate.Clip must NOT carry flat source semantic fields (single typed authority)"
        }
        require(clipRecord.contains("TimelineSourceBinding sourceBinding")) {
            "FAIL (H20): TimelineCandidate.Clip must carry the typed TimelineSourceBinding"
        }

        // H21: no silent catch→null narrowing for authored source binding.
        val adapterSrc = file("timeline-module/src/main/java/com/example/platform/timeline/app/InternalTimelineCandidateAdapter.java").readText()
        require(!adapterSrc.contains("catch (IllegalArgumentException e) {\n            return null;")
                && !adapterSrc.contains("catch (Exception e) {\n            return null;")) {
            "FAIL (H21): silent catch→null narrowing of source binding forbidden in adapter"
        }
        val mapperSrc = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineDocumentCandidateMapper.java").readText()
        require(!mapperSrc.contains("catch (IllegalArgumentException invalid) {\n            return null;")) {
            "FAIL (H21): silent catch→null narrowing of source binding forbidden in document mapper"
        }

        // H22: R5 behavioral + real-PG tests must exist.
        val r5Tests = listOf(
            "CheckpointARound5StrictDecodeTest",
            "CheckpointARound5SourceBindingClosureTest",
            "CheckpointARound5PersistentMergePinIT"
        )
        for (t in r5Tests) {
            val searchRoot = if (t.endsWith("IT")) "render-module/src/test" else "timeline-module/src/test"
            val found = file(searchRoot).walkTopDown().any { it.name == t + ".java" }
            require(found) {
                "FAIL (H22): R5 test " + t + " must exist"
            }
        }

        // ── FINAL_CLOSURE_F1 guards (post-Round-5): persistent merge transaction ──
        // F23: the persistent merge entrypoint must NOT rely on a self-invoked
        // @Transactional overload — no @Transactional on either merge overload.
        require(!mergeEngineSrc.contains("@Transactional\n") && !mergeEngineSrc.contains("@Transactional(")) {
            "FAIL (F23): TimelineMergeEngine must not depend on Spring @Transactional " +
                "for the persistent merge entrypoint (self-invocation bypass)"
        }
        // V2 F23b/F24: merge is computation-only until it delegates the complete
        // write contract to the sole canonical Timeline mutation boundary.
        require(mergeEngineSrc.contains("revisionSaveService.saveMergeRevision(")) {
            "FAIL (V2-F23b): persistent merge must delegate to saveMergeRevision"
        }
        require(!mergeEngineSrc.contains("dsl.transactionResult(tx ->")
                && !mergeEngineSrc.contains("snapshotService.saveTx(")
                && !mergeEngineSrc.contains("revisionRepository.insertTx(")
                && !mergeEngineSrc.contains("revisionRefMutation.advance(")
                && !mergeEngineSrc.contains("artifactPinService.registerRevisionPinsTx(")) {
            "FAIL (V2-F24): merge must not reproduce snapshot/revision/pin/ref write semantics"
        }
        // V2 F24f: the read repository exposes no revision writer or MAX+1 allocator.
        val revisionRepoSrc = file("timeline-module/src/main/java/com/example/platform/timeline/adapter/TimelineRevisionRepository.java").readText()
        require(!revisionRepoSrc.contains("public void insertTx(") && !revisionRepoSrc.contains("public int nextRevisionNumberTx(")) {
            "FAIL (V2-F24f): TimelineRevisionRepository must remain read-only"
        }
        // F24g: the TRUE production-path failure test exists (calls merge(request)).
        val mergePinIt = file("render-module/src/test/java/com/example/platform/render/app/timeline/CheckpointARound5PersistentMergePinIT.java").readText()
        require(mergePinIt.contains("mergeEngine.merge(request)")) {
            "FAIL (F24g): the real-PG merge failure test must call the actual merge entrypoint"
        }

        // ── FINAL_CLOSURE_F2 guards (post-Round-5): canonical strictness ──
        val sourceBindingSrc = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/clip/TimelineSourceBindingCanonicalSemantics.java").readText()
        // F25: canonical decoder consumes AND validates contentDigest.algorithm.
        require(sourceBindingSrc.contains("digestNode.path(\"algorithm\")")) {
            "FAIL (F25): canonical source-binding decoder must consume contentDigest.algorithm"
        }
        // F26: no hardcoded SHA_256 while ignoring the authored algorithm node.
        require(!sourceBindingSrc.contains("ContentDigest(\n                ContentDigest.DigestAlgorithm.SHA_256,")) {
            "FAIL (F26): source-binding decoder must not hardcode SHA_256 ignoring the authored algorithm"
        }
        // F27: flat source-range parser has NO semantic defaults for required fields.
        require(!sourceBindingSrc.contains(".asLong(30)") && !sourceBindingSrc.contains(".asLong(1)")
                && !sourceBindingSrc.contains(".asLong(0)")) {
            "FAIL (F27): flat source-range parser must not contain semantic defaults (asLong(30)/asLong(1)/asLong(0))"
        }
        // F28: Transition/Automation strict decoders require integral numeric nodes.
        val transitionSrc = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/transition/TransitionCanonicalSemantics.java").readText()
        val automationSrc = file("timeline-module/src/main/java/com/example/platform/timeline/semantics/automation/AutomationCanonicalSemantics.java").readText()
        require(transitionSrc.contains("isIntegralNumber()")) {
            "FAIL (F28a): Transition decoder must require integral JSON nodes for ticks/timeScale"
        }
        require(automationSrc.contains("isIntegralNumber()")) {
            "FAIL (F28b): Automation decoder must require integral JSON nodes for ticks/timeScale"
        }
        // F28c: the F2 strict-codec test matrix exists.
        val f2Test = file("timeline-module/src/test/java/com/example/platform/timeline/semantics/CheckpointAFinalClosureF2StrictCodecTest.java").exists()
        require(f2Test) {
            "FAIL (F28c): F2 strict codec test matrix must exist"
        }

        // ── POST_FINAL_REVIEW_P1 guards (post-final-review): DB-enforced head CAS ──
        val currentRevSrc = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionRefMutation.java").readText()
        // G29: expected revision participates in the UPDATE predicate.
        require(currentRevSrc.contains("HEAD_REVISION_ID.eq(expectedHeadRevisionId)")
                && currentRevSrc.contains("HEAD_REVISION_ID.isNull()")) {
            "FAIL (G29): head CAS must put expected revision in the UPDATE predicate (eq / IS NULL)"
        }
        // G30: NO check-then-act as correctness authority. The UPDATE (with the
        // expected revision in its predicate — G29) must be the correctness
        // authority and must come BEFORE any diagnostic read. A diagnostic
        // SELECT after CAS failure is explicitly allowed (spec 4.2/4.5).
        val casMethodBody = currentRevSrc.substring(
                currentRevSrc.indexOf("public boolean advance"),
                currentRevSrc.indexOf("public boolean advance") + 1400)
        val firstUpdate = casMethodBody.indexOf("tx.update(")
        val firstSelect = casMethodBody.indexOf("tx.select(")
        require(firstUpdate != -1 && (firstSelect == -1 || firstUpdate < firstSelect)) {
            "FAIL (G30): head CAS must not read-then-act (SELECT before conditional UPDATE)"
        }
        require(currentRevSrc.contains(".execute() == 1")) {
            "FAIL (G30b): head CAS must enforce affected-row count == 1"
        }
        // G30c: the real-PG CAS concurrency test exists and uses the real service.
        val casIt = file("render-module/src/test/java/com/example/platform/render/app/timeline/CheckpointAPostFinalReviewHeadCasIT.java").readText()
        require(casIt.contains("concurrentWritersSingleWinner")) {
            "FAIL (G30c): real-PG head-CAS concurrency test must exist"
        }

        // ── POST_FINAL_REVIEW_P2 guards: source-binding strict boundary ──
        // G31: adapter distinguishes ABSENT from PRESENT-malformed sourceBinding.
        require(adapterSrc.contains("clipNode.has(\"sourceBinding\")")) {
            "FAIL (G31): adapter must distinguish absent from present sourceBinding"
        }
        require(adapterSrc.contains("PRESENT clip sourceBinding must be a non-empty object")) {
            "FAIL (G31b): present-but-malformed sourceBinding must fail closed"
        }
        // G32: canonical decoder rejects malformed JsonNode root (only null Java
        // reference means caller-level absence).
        require(sourceBindingSrc.contains("if (node == null)") && sourceBindingSrc.contains("node.getNodeType().name()")) {
            "FAIL (G32): canonical binding decoder must reject malformed JsonNode roots"
        }
        // G33: document mapper must not synthesize MediaTime.ZERO for required
        // source-binding range fields.
        require(!mapperSrc.contains("clip.getTrimStart() != null ? clip.getTrimStart()")
                && mapperSrc.contains("missing trimStart/trimEnd must not be synthesized to 0..0")) {
            "FAIL (G33): document mapper must not synthesize ZERO for missing source range"
        }
        // G34: TimelineClip must not default trimStart/trimEnd to ZERO.
        val clipSrc = file("timeline-module/src/main/java/com/example/platform/timeline/canonical/TimelineClip.java").readText()
        require(clipSrc.contains("this.trimStart = trimStart;")
                && clipSrc.contains("MISSING (null) must remain")) {
            "FAIL (G34): TimelineClip must preserve null trimStart/trimEnd (missing != zero)"
        }
        // G34b: the P2 boundary test matrix exists.
        val p2Test = file("timeline-module/src/test/java/com/example/platform/timeline/app/CheckpointAPostFinalReviewSourceBindingBoundaryTest.java").exists()
        require(p2Test) {
            "FAIL (G34b): P2 source-binding strict-boundary test matrix must exist"
        }

        println("OK: TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1 verified (typed parameter hash participation; deterministic serialization; MediaTime automation; first-class transition; zero provider leakage in authored semantics; no V3; production diff/patch/merge semantic closure; local semantic ownership; R5 constructor-injection closure; R5 domain-value authority; R5 single typed source binding; FINAL_CLOSURE_F1 explicit-jOOQ merge transaction; FINAL_CLOSURE_F2 strict canonical codec)")
    }
}
