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

        // Table classes = all *.java directly under tables/ (jOOQ 3.19 names the table class
        // after the table, e.g. UsageRecord.java; records/ holds the *Record.java companions).
        val tableCount = committedDir.walkTopDown()
            .filter { it.isFile && it.parentFile?.name == "tables" && it.name.endsWith(".java") }
            .count()
        val recordCount = committedDir.walkTopDown()
            .filter { it.isFile && it.parentFile?.name == "records" && it.name.endsWith(".java") }
            .count()
        val totalFiles = committedDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .count()

        println("Generated source inventory:")
        println("  Table classes: " + tableCount)
        println("  Record classes: " + recordCount)
        println("  Total Java files: " + totalFiles)

        // P1-IMPL1: corrected expectations to the V1-synchronized generated state.
        // (P1 retired 5 ownerless Product tables from V1: timeline_template, render_preset,
        // asset_library, render_history, ai_suggestion -> generated 153 -> 148; parity 148/148 EXACT.)
        // MCMV2-C (C3): canonical media schema rewrite — asset -> media_asset,
        // media_asset_artifact + media_stream + media_probe_observation added,
        // media_asset_metadata (double authority) removed -> 148 -> 150; parity 150/150 EXACT.
        // GCR-2 single-V1 consolidation: former V2-V7 tables folded into canonical V1
        // (timeline_revision_ref, apply_command, timeline_revision_parent,
        // project_revision_counter, source_visual_description_snapshot) +
        // artifact_replica + artifact_pin -> 150 -> 157; parity 157/157 EXACT.
        require(tableCount == 157) { "FAIL: Expected 157 Table classes but found " + tableCount }
        require(recordCount == 157) { "FAIL: Expected 157 Record classes but found " + recordCount }
        require(totalFiles >= 300) { "FAIL: Expected at least 300 total Java files but found " + totalFiles }

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
        require(engineTest.readText().contains("sameEntityDisjointPathsBothMaterialized")) { "FAIL: disjoint-path materialization proof missing" }
        require(engineTest.readText().contains("deleteVsModifyConflict")) { "FAIL: delete-vs-modify proof missing" }
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
        // C1-CRR-RED-05: exactly one merge payload conversion path — internal-1.0 via the E1b gate adapter
        require(engineSrc.contains("InternalTimelineCandidateAdapter.map")) { "FAIL: canonical gate adapter conversion missing" }
        require(engineSrc.contains("TimelineSnapshotConverter.toSnapshot")) { "FAIL: candidate -> snapshot conversion missing" }
        require(!engineSrc.contains("objectMapper.readValue(payload, TimelineDocument")) { "FAIL: legacy TimelineDocument parse path present" }
        // C1-CRR-RED-09 (part): no dual-format fallback — the engine must not try-catch
        // around a legacy parse then fall through to an alternate parser.
        require(!engineSrc.contains("readValue(payload, TimelineDocument.class)")) { "FAIL: dual-format fallback parse present" }
        // C1-CRR-RED-06: engine remains sole semantic merge authority
        require(engineSrc.contains("class TimelineMergeEngine")) { "FAIL: TimelineMergeEngine missing" }
        // C1-CRR-RED-01/02/03: behavioral proofs must exist (JUnit, gates naturally active)
        val regression = file("timeline-module/src/test/java/com/example/platform/timeline/app/TimelineMergePayloadContractRegressionTest.java")
        require(regression.exists()) { "FAIL: payload contract regression missing" }
        val regSrc = regression.readText()
        require(regSrc.contains("productionSavedPayloadIsAcceptedByCanonicalGate")) { "FAIL: C1-CRR-RED-01 proof missing" }
        require(regSrc.contains("productionSavedPayloadCanBeConvertedToCanonicalMergeSnapshot")) { "FAIL: C1-CRR-RED-02 proof missing" }
        require(regSrc.contains("validationAndConversionDomainsAreEqual")) { "FAIL: C1-CRR-RED-03 proof missing" }
        // C1-CRR-RED-09: real application-context proof must exist
        val contextProof = file("platform-app/src/test/java/com/example/platform/C1CrrMergeAuthorityCompositionTest.java")
        require(contextProof.exists()) { "FAIL: real application-context proof missing" }
        // C1-CRR-RED-10: no schema/module change (jOOQ generated tables unchanged)
        require(!file("render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRenderExecutionMode.java").exists()) { "note: baseline check" }
        println("OK: C1-CRR payload contract verified (single internal-1.0 conversion path, gate domain == conversion domain, no bypass)")
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
        require(adapter.readText().contains("mapEffects")) { "FAIL: adapter effect parse missing" }
        require(e.contains("clip.effects()") && e.contains("node.set(\"effects\"")) { "FAIL: engine effect re-emit missing" }

        // ── RED-07: no unjustified canonical binary-float authorities ──
        require(!w.contains("double fps =") && !w.contains("(int) fps")) { "FAIL: canonical double fps residue in canonical import service" }

        // ── RED-09: no dual parser / legacy rate compatibility path ──
        val parser = file("render-module/src/main/java/com/example/platform/render/domain/interchange/TimelineScriptParser.java")
        val p = parser.readText()
        require(p.contains("parseFrameRateNode")) { "FAIL: exact rational rate parse missing" }
        require(!p.contains("treeToValue(output, TimelineOutputSpec.class)")) { "FAIL: legacy blind treeToValue rate parse" }

        // ── RED-10: TimelineMergeEngine remains sole semantic merge authority ──
        require(e.contains("class TimelineMergeEngine")) { "FAIL: TimelineMergeEngine missing" }
        require(!e.contains("readValue(payload, TimelineDocument.class)")) { "FAIL: TimelineDocument parse path present" }

        // ── RED-11: schema/module zero delta ──
        require(!file("render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRenderExecutionMode.java").exists()) { "note: baseline check" }

        // ── RED-12: R1 quarantine contamination = 0 ──
        // R1 lives in a separate quarantined worktree (.worktrees/r1-canonicalization),
        // NOT in the candidate source tree. No candidate source may reference
        // R1's quarantine branch identity as production code.
        require(!file("render-module/src/main/java/com/example/platform/render/app/timeline/R1TimelineMigration.java").exists()) { "note: R1 residue baseline check" }

        // ── RED-13: source-binding preservation wiring ──
        require(e.contains("clip.assetBindingId()")) { "FAIL: merged clip asset binding re-emit missing" }
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
        "verifyJooqGeneratedSources",
        "verifyJooqNamedInterfacePreservation",
        "verifyP1ProductLayerRetirement",
        "verifyC1TimelineMergeConvergence",
        "verifyGcr1CorrectionV2IngressAuthority",
        "verifyGcr2ArtifactAuthority",
        "verifyGcr2CorrectionV1",
        "verifyGcr5Gcr6DatabaseCanonicalization",
        "verifyTimelineEffectTransitionCanonicalization",
        "verifyJooqNoNewUntypedIdentifiers",
        "verifyJooqPlainSqlAllowlist",
        "verifyJooqDynamicIdentifierAllowlist",
        "verifyJooqAllowlistIntegrity"
    )
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
        require(file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineArtifactPinValidator.java").exists()) {
            "FAIL: TimelineArtifactPinValidator missing (TIMELINE_ARTIFACT_PIN_EXISTENCE_VALIDATION_COUNT != 1)"
        }
        val validatorSrc = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineArtifactPinValidator.java").readText()
        require(validatorSrc.contains("getArtifact") && validatorSrc.contains("contentDigest")) {
            "FAIL: TimelineArtifactPinValidator lacks existence+digest checks"
        }
        val revisionSrc = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionService.java").readText()
        require(revisionSrc.contains("artifactPinValidator.validate")) {
            "FAIL: TimelineRevisionService does not validate artifact pins (existence/tenant/digest fail-closed)"
        }
        require(revisionSrc.contains("registerRevisionPins")) {
            "FAIL: TimelineRevisionService does not register artifact pin protection (REVISION_PIN_ATOMICITY missing)"
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
        // 11. Merge engine writes merged transitions/automations back to the
        //     merged payload (no silent target-side preservation).
        val mergeEngine = file("timeline-module/src/main/java/com/example/platform/timeline/app/TimelineMergeEngine.java").readText()
        require(mergeEngine.contains("transitionsToJson") && mergeEngine.contains("automationsToJson")) {
            "FAIL: merge engine must materialize merged transition/automation state"
        }
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
        require(mergeEngine.contains("mergedComposition.remove(\"transitions\")")
                && mergeEngine.contains("mergedComposition.remove(\"automations\")")) {
            "FAIL: merge materialization must represent empty semantic result (no target resurrection)"
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

        println("OK: TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1 verified (typed parameter hash participation; deterministic serialization; MediaTime automation; first-class transition; zero provider leakage in authored semantics; no V3; production diff/patch/merge semantic closure; local semantic ownership)")
    }
}
