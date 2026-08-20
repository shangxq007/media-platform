plugins {
    id("java-library")
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":typed-schema-module"))
    implementation(project(":extension-module")) // OM: ContractVersion reuse (no cycle: extension -> shared-kernel/billing)
    implementation(project(":media-module")) // MCMV2-C: Media Canonical Model (frozen direction: Render -> Media)
    implementation(project(":audio-module")) // AUDIO_V2: canonical Audio Mix authority (frozen direction: Render -> Audio)
    implementation(project(":font-text-module")) // ROADMAP_19: canonical Font/Text value semantics (frozen direction: Render -> FontText)
    implementation(project(":color-image-module")) // ROADMAP20: typed color/image value semantics (frozen direction: Render -> ColorImage)
    implementation(project(":platform-algorithms:graph")) // ROADMAP20: graph kernel mechanics for RenderGraph (C30)
    api(project(":timeline-module")) // GCR-1: canonical Timeline semantics (frozen direction: Render -> Timeline)
    api(project(":operation-module")) // GCR-1: canonical Operation semantics (frozen direction: Render -> Operation)
    implementation(project(":notification-module")) // NotificationEventPublisher rehomed to notification (K2)
    api(project(":shared-kernel"))
    testImplementation(testFixtures(project(":shared-kernel")))
    api(project(":ai-module"))
    api(project(":storage-module"))
    api(project(":artifact-module")) // GCR-2: canonical Artifact authority (frozen direction: Render -> Artifact; no cycle: artifact-module no longer depends on render)
    api(project(":extension-module"))
    api(project(":entitlement-module"))
    api(project(":billing-module"))
    api(project(":quota-billing-module"))
    api(project(":outbox-event-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("com.yomahub:liteflow-spring-boot-starter:2.15.3.2")
    // api("org.bytedeco:javacv-platform:1.5.9") // Removed: JavaCV deprecated, use FFmpeg CLI
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("software.amazon.awssdk:s3:2.29.45")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("native-media")
    }
    systemProperty("media.platform.localSmoke.enabled", System.getProperty("media.platform.localSmoke.enabled") ?: "")
    systemProperty("media.platform.localSmoke.strict", System.getProperty("media.platform.localSmoke.strict") ?: "")
    systemProperty("media.platform.localSmoke.outputRoot", System.getProperty("media.platform.localSmoke.outputRoot") ?: "")
}

tasks.register<Test>("nativeMediaTest") {
    description = "Runs native FFmpeg/media integration tests (requires FFmpeg and codec libraries)"
    group = "verification"
    useJUnitPlatform {
        includeTags("native-media")
    }
    shouldRunAfter(tasks.test)
    maxParallelForks = 1
    forkEvery = 1
}

tasks.register("verifyC20RenderPlanBoundaryGuard") {
    group = "verification"
    description = "ROADMAP20-C20: logical RenderPlan/RenderGraph package is provider-neutral, kernel-delegation-bound, and free of forbidden physical/legacy tokens (C18/C30)"
    doLast {
        val pkgDir = file("src/main/java/com/example/platform/render/domain/renderplan")
        require(pkgDir.exists() && pkgDir.isDirectory) {
            "FAIL: renderplan package dir missing: ${pkgDir.path}"
        }
        val javaFiles = fileTree(pkgDir) { include("**/*.java") }.files
        require(javaFiles.isNotEmpty()) {
            "FAIL: no .java files found under ${pkgDir.path}"
        }

        // Forbidden tokens (C18 provider neutrality + C30 kernel delegation): none of
        // these may appear outside of comment lines. Comment lines (starting with //
        // or *) are excluded so that javadoc/class-header mentions do not trip the gate.
        val forbidden = listOf(
            "ffmpeg", "Ffmpeg", "vulkan", "Vulkan", "webgpu", "WebGPU", "cuda", "CUDA",
            "opencue", "OpenCue", "org.springframework", "org.jooq",
            "com.example.platform.workflow", "com.example.platform.execution",
            "com.example.platform.typedschema", "Map<String", "HashMap", "HashSet",
            "UUID.randomUUID", "topologicalSort", "Kahn"
        )
        val violations = mutableListOf<String>()
        for (javaFile in javaFiles) {
            val lines = javaFile.readLines()
            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trimStart()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue
                }
                for (token in forbidden) {
                    if (line.contains(token)) {
                        violations.add("${javaFile.name}:${index + 1}: forbidden token '$token'")
                    }
                }
            }
        }
        require(violations.isEmpty()) {
            "FAIL: forbidden tokens present in renderplan package:\n" + violations.joinToString("\n")
        }

        // REQUIRED: at least one reference to the graph kernel (C30 delegation)
        val kernelRef = javaFiles.any { it.readText().contains("com.example.platform.graph") }
        require(kernelRef) {
            "FAIL: no reference to com.example.platform.graph (graph kernel delegation) in renderplan package"
        }

        // R2 B1: verified revision projection boundary must be the primary
        // planning input; the old arbitrarily-assembled hydrated record must not
        // be resurrected as a planning input type.
        val verifiedTypePresent = javaFiles.any { it.name == "VerifiedTimelineRevision.java" }
        require(verifiedTypePresent) {
            "FAIL: VerifiedTimelineRevision.java missing (R2 B1 verified projection boundary)"
        }
        val factoryPresent = javaFiles.any { it.name == "VerifiedTimelineRevisionFactory.java" }
        require(factoryPresent) {
            "FAIL: VerifiedTimelineRevisionFactory.java missing (R2 B1 verified hydration boundary)"
        }
        val hydratedGone = javaFiles.none { it.name == "HydratedTimelineRevision.java" }
        require(hydratedGone) {
            "FAIL: HydratedTimelineRevision.java must be removed (R2 B1: arbitrary assembly is not a verified boundary)"
        }
        val planningInputUsesVerified = javaFiles.any {
            it.name == "RenderPlanningInput.java"
                    && it.readText().contains("VerifiedTimelineRevision")
        }
        require(planningInputUsesVerified) {
            "FAIL: RenderPlanningInput must consume VerifiedTimelineRevision (R2 B1)"
        }

        // R2 B2: TimedText materialization must carry complete StyledText
        // semantics (content + semantic runs + style runs + paragraph style).
        val timedTextHasStyledText = javaFiles.any {
            it.name == "TimedTextMaterializationRequirement.java"
                    && it.readText().contains("StyledText styledText")
        }
        require(timedTextHasStyledText) {
            "FAIL: TimedTextMaterializationRequirement must carry StyledText (R2 B2 complete text WHAT)"
        }

        // R2 B3: no Object.toString() reliance in the canonical codec's
        // fingerprint path — frame/run/fallback must be encoded explicitly.
        val codec = javaFiles.find { it.name == "RenderPlanCanonicalCodec.java" }
        require(codec != null && codec.readText().contains("textFrameCanonical")
                && codec.readText().contains("resolvedFontRunCanonical")
                && codec.readText().contains("styledTextCanonical")) {
            "FAIL: RenderPlanCanonicalCodec must explicitly encode TextFrame/ResolvedFontRun/StyledText (R2 B3 value-deterministic)"
        }

        println("OK: ROADMAP20 C20 RenderPlan boundary guard passed (provider-neutral, kernel-bound, R2 B1/B2/B3, ${javaFiles.size} files)")
    }
}
