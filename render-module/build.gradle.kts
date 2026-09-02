plugins {
    id("java-library")
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":sandbox-isolation-module"))
    implementation(project(":typed-schema-module"))
    implementation(project(":extension-module")) // OM: ContractVersion reuse (no cycle: extension -> shared-kernel/billing)
    implementation(project(":media-module")) // MCMV2-C: Media Canonical Model (frozen direction: Render -> Media)
    implementation(project(":audio-module")) // AUDIO_V2: canonical Audio Mix authority (frozen direction: Render -> Audio)
    implementation(project(":font-text-module")) // ROADMAP_19: canonical Font/Text value semantics (frozen direction: Render -> FontText)
    implementation(project(":color-image-module")) // ROADMAP20: typed color/image value semantics (frozen direction: Render -> ColorImage)
    implementation(project(":platform-algorithms:graph")) // ROADMAP20: graph kernel mechanics for RenderGraph (C30)
    api(project(":timeline-module")) // GCR-1: canonical Timeline semantics (frozen direction: Render -> Timeline)
    api(project(":operation-module")) // GCR-1: canonical Operation semantics (frozen direction: Render -> Operation)
    api(project(":shared-kernel"))
    testImplementation(testFixtures(project(":shared-kernel")))
    testImplementation(project(":media-execution-plan-module")) // R21 C6-C guarded-entry boundary tests (test-only; no production cycle)
    api(project(":ai-module"))
    api(project(":storage-module"))
    api(project(":artifact-module")) // GCR-2: canonical Artifact authority (frozen direction: Render -> Artifact; no cycle: artifact-module no longer depends on render)
    api(project(":extension-module"))
    api(project(":outbox-event-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("com.yomahub:liteflow-spring-boot-starter:2.15.3.2")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("software.amazon.awssdk:s3:2.29.45")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
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
        // NOTE (ROADMAP20 FINAL): generic collection tokens such as Map<String /
        // HashMap / LinkedHashMap were REMOVED from this list — derived local
        // computation inside the verified-snapshot view layer is legitimate
        // implementation detail, NOT semantic authority. Authority surfaces are
        // checked structurally below (STRUCTURAL_GUARDS_CHECK_AUTHORITY_SURFACES_NOT_GENERIC_LANGUAGE_TOKENS_V1).
        val forbidden = listOf(
            "vulkan", "Vulkan", "webgpu", "WebGPU", "cuda", "CUDA",
            "opencue", "OpenCue", "org.springframework", "org.jooq",
            "com.example.platform.workflow", "com.example.platform.execution",
            "com.example.platform.typedschema",
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

        // R3-G1: primary planning input consumes ONE verified authored snapshot;
        // arbitrary EffectInstance/EffectDefinition fragments cannot enter the
        // planning API independently of the verified boundary.
        val planningInputSource = javaFiles.find { it.name == "RenderPlanningInput.java" }?.readText()
        require(planningInputSource != null && planningInputSource.contains("VerifiedRenderSemanticSnapshot authoredSnapshot")
                && !planningInputSource.contains("List<EffectInstance>")) {
            "FAIL: RenderPlanningInput must consume VerifiedRenderSemanticSnapshot and expose no List<EffectInstance> fragment (R3-G1)"
        }
        val authoredSnapshotPresent = javaFiles.any { it.name == "VerifiedRenderSemanticSnapshot.java" }
        require(authoredSnapshotPresent) {
            "FAIL: VerifiedRenderSemanticSnapshot.java missing (R3-G1 complete authored semantics boundary)"
        }
        val effectSnapshotFactoryPresent = javaFiles.any { it.name == "VerifiedEffectSemanticSnapshotFactory.java" }
        require(effectSnapshotFactoryPresent) {
            "FAIL: VerifiedEffectSemanticSnapshotFactory.java missing (R3-G1 effect state verification)"
        }

        // R3-G2: no generic unknown canonical fallback token in fingerprint paths.
        require(codec != null && !codec.readText().contains("UNKNOWN_VARIANT")) {
            "FAIL: RenderPlanCanonicalCodec must not contain UNKNOWN_VARIANT generic fallback (R3-G2/M1 fail-closed)"
        }

        // R3-G3: variable-length canonical sections must use explicit count framing.
        require(codec != null && codec.readText().contains("private void counted(StringBuilder")) {
            "FAIL: RenderPlanCanonicalCodec must use count-framed variable-length sections (R3-G2/B2 structural framing)"
        }

        // R4-M2: RenderPlan carries the authoritative Effect semantic reference
        // and the fingerprint includes it (structural assertions).
        val renderPlanSource = javaFiles.find { it.name == "RenderPlan.java" }?.readText()
        require(renderPlanSource != null && renderPlanSource.contains("EffectSemanticReference effectSemanticReference")) {
            "FAIL: RenderPlan must carry the authoritative Effect semantic reference (R4-A2/M2)"
        }
        require(codec != null && codec.readText().contains("effectSemanticReference.effectStateDigest()")) {
            "FAIL: RenderPlan fingerprint must include the authored Effect semantic digest (R4-A3/M2)"
        }
        val provenanceSource = javaFiles.find { it.name == "RenderPlanProvenance.java" }?.readText()
        require(provenanceSource != null && provenanceSource.contains("EffectSemanticReference effectSemanticReference")) {
            "FAIL: RenderPlanProvenance must expose the Effect semantic reference (R4-A4/M2)"
        }
        val bindingFactorySource = javaFiles.find { it.name == "VerifiedRenderSemanticSnapshotFactory.java" }?.readText()
        require(bindingFactorySource != null
                && !bindingFactorySource.contains("effectBinding")
                && !bindingFactorySource.contains("EffectSemanticBinding")
                && bindingFactorySource.contains("EffectSemanticSnapshotReference")) {
            "FAIL: authored snapshot factory must fail closed on cross-revision effect binding (R4-A1/M2) — caller binding parameters retired; verified boundary accepts (snapshot, exact pin) only"
        }
        // cross-revision + binding-tamper fail-closed now lives in the pin
        // verifier (BI2/RP3-C): id equality between snapshot and reference.
        val effectVerifiedFactorySource = javaFiles.find { it.name == "VerifiedEffectSemanticSnapshotFactory.java" }?.readText()
        require(effectVerifiedFactorySource != null && effectVerifiedFactorySource.contains("snapshot.id().equals(expectedReference.snapshotId())")) {
            "FAIL: verified factory must enforce exact snapshot id == pin snapshotId (BI2/RP3-C)"
        }

        // R4-B: no delimiter-based pair flattening in Effect canonical/identity
        // paths; the single shared pair encoder must be used.
        require(codec != null
                && !codec.readText().contains("p.key() + \":\" + p.value()")
                && !codec.readText().contains("parameter.key() + \"=\" + parameter.value()")
                && codec.readText().contains("EffectSemanticStateCanonicalSemantics.encodeParameterPair")) {
            "FAIL: Effect parameter pairs must use the single shared pair encoder (R4-B/M2)"
        }
        val materializerSource = javaFiles.find { it.name == "DefaultRenderMaterializer.java" }?.readText()
        require(materializerSource != null
                && !materializerSource.contains("parameter.key() + \"=\" + parameter.value()")
                && !materializerSource.contains("parameter.key() + \":\" + parameter.value()")) {
            "FAIL: node requirement identity must not use delimiter flattening (R4-B/M2; R6-C2 single encoder)"
        }

        // R4-M: ColorDescription canonicalizer has explicit fail-closed branch.
        require(codec != null && codec.readText().contains("Unsupported ColorDescription variant")) {
            "FAIL: ColorDescription canonicalizer must fail closed on unknown variant (R4-M1/M2)"
        }

        // R5-M2: no public caller-mintable binding path — the only issuance is
        // the Timeline/Effect domain authority (authority types live in the
        // timeline-module semantics/effect package; render consumes them).
        val timelineEffectDir = file("../timeline-module/src/main/java/com/example/platform/timeline/semantics/effect")
        val timelineEffectFiles = fileTree(timelineEffectDir) { include("*.java") }.files
        // CLEAN-FORWARD (CF6/CF7/CF8): old canonical issuance authorities were
        // DELETED, not deprecated — no compatibility surface may exist.
        require(timelineEffectFiles.none { it.name == "EffectSemanticBinding.java" }) {
            "FAIL: EffectSemanticBinding must be DELETED (CF7 — no compatibility authority)"
        }
        require(timelineEffectFiles.none { it.name == "AuthoredEffectSemanticAuthority.java" }) {
            "FAIL: AuthoredEffectSemanticAuthority must be DELETED (CF6 — no compatibility issuance)"
        }
        require(timelineEffectFiles.none { it.name == "RevisionOwnedEffectProjection.java" }) {
            "FAIL: RevisionOwnedEffectProjection must be DELETED (CF8 — no compatibility projection authority)"
        }
        // CLEAN-FORWARD (CF1/CF2): TimelineRevision requires semanticContext +
        // Effect pin — no compatibility constructor without them.
        val revisionSource = file("../timeline-module/src/main/java/com/example/platform/timeline/version/TimelineRevision.java").readText()
        require(revisionSource.contains("semanticContext == null") && revisionSource.contains("REQUIRES_SEMANTIC_CONTEXT")) {
            "FAIL: TimelineRevision must require semanticContext by construction (CF1)"
        }
        require(revisionSource.contains("effectReference() == null") && revisionSource.contains("REQUIRES_EFFECT_SEMANTIC_SNAPSHOT_REFERENCE")) {
            "FAIL: TimelineRevision must require the Effect pin by construction (CF2)"
        }
        // CLEAN-FORWARD (F): no legacy timeline-only semantic version.
        val contextSource = file("../timeline-module/src/main/java/com/example/platform/timeline/version/TimelineRevisionSemanticContext.java").readText()
        require(!contextSource.contains("timeline-only-v1")) {
            "FAIL: legacy timeline-only semantic version must NOT exist (clean-forward)"
        }
        require(contextSource.contains("REVISION_SEMANTICS_V1")) {
            "FAIL: revision-semantics-v1 must be the single valid contract"
        }
        val materializerSource2 = javaFiles.find { it.name == "DefaultRenderMaterializer.java" }?.readText()
        require(materializerSource2 != null
                && materializerSource2.contains("ofComplete(")
                && materializerSource2.contains("effect.effectInstanceId()")
                && materializerSource2.contains("target() instanceof ClipEffectTarget")) {
            "FAIL: materializer must build complete Logical Effect WHAT via ofComplete + typed target (R5-B/R6-A)"
        }
        require(codec != null && codec.readText().contains("effect.effectInstanceId()")
                && codec.readText().contains("effect.effectDefinitionVersion()")
                && codec.readText().contains("effect.applicationRange()")
                && codec.readText().contains("effect.temporalBehavior()")) {
            "FAIL: RenderPlan canonical encoding must include complete Effect WHAT fields (R5-B)"
        }
        // R5-F: unordered collections deep-sorted in the domain authority.
        val effectSemanticsSource = timelineEffectFiles.find { it.name == "EffectSemanticStateCanonicalSemantics.java" }
        require(effectSemanticsSource != null
                && effectSemanticsSource.readText().contains(".sorted(java.util.Comparator.comparing(Enum::name))")
                && effectSemanticsSource.readText().contains(".stream().sorted()")) {
            "FAIL: unordered Effect collections must be deep-sorted (R5-F)"
        }

        // ── R6 structural guard ─────────────────────────────────────────────
        // 1. EffectTarget + ClipEffectTarget exist in the Effect domain.
        require(timelineEffectFiles.any { it.name == "EffectTarget.java" }
                && timelineEffectFiles.any { it.name == "ClipEffectTarget.java" }) {
            "FAIL: typed EffectTarget root + ClipEffectTarget variant required (R6-A)"
        }
        // 2. CLEAN-FORWARD (CF8): RevisionOwnedEffectProjection deleted — the
        //    new authority derives target context from the canonical document.
        require(timelineEffectFiles.none { it.name == "RevisionOwnedEffectProjection.java" }) {
            "FAIL: RevisionOwnedEffectProjection must be DELETED (CF8)"
        }
        // 3. authority verifies target clips against the canonical document
        //    (no overlap-only heuristic, no trackId-string heuristics).
        val r6AuthoritySource = timelineEffectFiles.find { it.name == "EffectSemanticSnapshotAuthority.java" }?.readText()
        require(r6AuthoritySource != null && r6AuthoritySource.contains("resolveTargetContext")
                && r6AuthoritySource.contains("does not exist in the canonical document")
                && !r6AuthoritySource.contains("\\\"audio\\\".equals")) {
            "FAIL: authority must resolve targets from the canonical document (no trackId heuristics, R6-A/B5)"
        }
        // 4. authority is an INSTANCE (registry + store injected) — no public
        //    static mint with caller-supplied id/registry.
        require(r6AuthoritySource != null && r6AuthoritySource.contains("EffectDefinitionVersionRegistry registry")
                && r6AuthoritySource.contains("EffectSemanticSnapshotStore store")
                && !r6AuthoritySource.contains("public static EffectSemanticSnapshot mint")) {
            "FAIL: production mint authority must be instance-based with injected durable registry/store (B3/B4)"
        }
        // 5. EffectInstance carries typed target.
        val r6EffectInstanceSource = timelineEffectFiles.find { it.name == "EffectInstance.java" }?.readText()
        require(r6EffectInstanceSource != null && r6EffectInstanceSource.contains("EffectTarget target")) {
            "FAIL: EffectInstance must carry typed EffectTarget (R6-A)"
        }
        // 6. target participates in domain canonical semantics.
        require(effectSemanticsSource != null && effectSemanticsSource.readText().contains("targetTrack")) {
            "FAIL: Effect target must participate in domain canonical digest (R6-F)"
        }
        // 7. no effectsForClip overlap association in the materializer.
        val r6MaterializerSource = javaFiles.find { it.name == "DefaultRenderMaterializer.java" }?.readText()
        require(r6MaterializerSource != null && r6MaterializerSource.contains("target() instanceof ClipEffectTarget")
                && !r6MaterializerSource.contains("applicationRange().overlaps(")) {
            "FAIL: effectsForClip must select by typed target, not overlap (R6-A6)"
        }
        // 8. EffectMaterializationRequirement contains target; no broken factories.
        val r6EffectReqSource = javaFiles.find { it.name == "EffectMaterializationRequirement.java" }?.readText()
        require(r6EffectReqSource != null && r6EffectReqSource.contains("EffectTarget target")
                && !r6EffectReqSource.contains("static EffectMaterializationRequirement of(")
                && !r6EffectReqSource.contains("ofSorted(")) {
            "FAIL: EffectMaterializationRequirement requires target; of()/ofSorted() must be gone (R6-D)"
        }
        // 9. requiredCapabilities consumed by capability lowering (union rule).
        val r6VocabSource = javaFiles.find { it.name == "RenderCapabilityVocabulary.java" }?.readText()
        require(r6VocabSource != null && r6VocabSource.contains("forRequiredCapability")
                && r6VocabSource.contains("definitionRequiredCapabilities")) {
            "FAIL: definition requiredCapabilities must be lowered via platform authority (R6-B)"
        }
        val r6MaterializerSource2 = javaFiles.find { it.name == "DefaultRenderMaterializer.java" }?.readText()
        require(r6MaterializerSource2 != null && r6MaterializerSource2.contains("definition.requiredCapabilities()")) {
            "FAIL: materializer must consume EffectDefinition.requiredCapabilities (R6-B)"
        }
        // 10. node identity uses the complete effect logical requirement canonical
        // (single encoder shared with final plan serialization).
        require(codec != null && codec.readText().contains("effectMaterializationRequirementCanonical")) {
            "FAIL: single effect logical requirement canonical encoder required (R6-C2)"
        }
        require(r6MaterializerSource2 != null
                && r6MaterializerSource2.contains("CODEC.effectMaterializationRequirementCanonical(")) {
            "FAIL: effect node identity must use the single canonical encoder (R6-C)"
        }
        // 11. R6-H: effect stack order is ORDERED — canonical state must NOT
        // re-sort EffectInstance by id.
        require(effectSemanticsSource != null
                && !effectSemanticsSource.readText()
                        .contains("sorted(java.util.Comparator.comparing(EffectInstance::effectInstanceId))")) {
            "FAIL: authored effect stack order must be preserved (R6-H ORDERED); no instance-id re-sort"
        }
        // 12. final plan still carries the global EffectSemanticReference (R4-A2).
        val r6RenderPlanSource = javaFiles.find { it.name == "RenderPlan.java" }?.readText()
        require(r6RenderPlanSource != null && r6RenderPlanSource.contains("EffectSemanticReference")) {
            "FAIL: final RenderPlan must retain EffectSemanticReference (R4-A2 preserved)"
        }

        // ── ROADMAP20 FINAL implementation guard ─────────────────────────────
        // 13. no public caller snapshot minting — EffectSemanticSnapshot ctor is
        //     package-private; only the authority mints.
        val snapshotCtorSource = timelineEffectFiles.find { it.name == "EffectSemanticSnapshot.java" }?.readText()
        require(snapshotCtorSource != null && !snapshotCtorSource.contains("public EffectSemanticSnapshot(")) {
            "FAIL: EffectSemanticSnapshot must NOT have a public constructor (minting is domain-authority only)"
        }
        // 14. snapshot id excluded from the canonical semantic digest encoder —
        //     the canonical content encoder itself must not reference the id;
        //     error-message mentions of the id (diagnostics) are allowed.
        val snapshotCanonicalSource = timelineEffectFiles.find { it.name == "EffectSemanticSnapshotCanonicalSemantics.java" }?.readText()
        require(snapshotCanonicalSource != null
                && snapshotCanonicalSource.contains("id EXCLUDED")
                && !snapshotCanonicalSource.contains("snapshot.id().value() + \"@\"")
                && !snapshotCanonicalSource.contains("snapshot.id().value() + \"#\"")) {
            "FAIL: snapshot id must NOT participate in canonical semantic digest (EFFECT_SNAPSHOT_HANDLE_DOES_NOT_PARTICIPATE_IN_CANONICAL_SEMANTIC_DIGEST_V1)"
        }
        // 15. supportedBackendCapabilities excluded from definition semantic digest.
        val definitionCanonicalSource = timelineEffectFiles.find { it.name == "EffectDefinitionCanonicalSemantics.java" }?.readText()
        require(definitionCanonicalSource != null && definitionCanonicalSource.contains("supportedBackendCapabilities is NOT part")) {
            "FAIL: supportedBackendCapabilities must not enter canonical definition digest (provider-neutrality)"
        }
        // 16. authority enforces V1 automation fail-closed.
        val authoritySource2 = timelineEffectFiles.find { it.name == "EffectSemanticSnapshotAuthority.java" }?.readText()
        require(authoritySource2 != null && authoritySource2.contains("automationBindings") && authoritySource2.contains("unsupported in effect-semantics-v1")) {
            "FAIL: non-empty unverified automation must FAIL CLOSED at the domain authority (SA5)"
        }
        // 17. Render verified factory accepts only (snapshot, exact reference) —
        //     no effects/definitions/projection/caller-binding parameters.
        val verifiedFactorySource = javaFiles.find { it.name == "VerifiedEffectSemanticSnapshotFactory.java" }?.readText()
        require(verifiedFactorySource != null && verifiedFactorySource.contains("EffectSemanticSnapshotReference expectedReference")
                && !verifiedFactorySource.contains("List<EffectInstance> effects")) {
            "FAIL: verified factory must accept only (snapshot, pin) — caller effects/definitions authority removed"
        }
        // 18. Render materializer consumes derived per-clip effect views from the
        //     verified snapshot (not arbitrary lists).
        val materializerSource5 = javaFiles.find { it.name == "DefaultRenderMaterializer.java" }?.readText()
        require(materializerSource5 != null && materializerSource5.contains("effectsForClip(clip)")) {
            "FAIL: materializer must derive per-clip effects from the verified snapshot"
        }
        // 19. no mutable-latest EffectDefinition lookup in render path — no
        //     latestEffectDefinition / latestEffectSnapshot tokens.
        require(javaFiles.none { it.readText().contains("latestEffectDefinition") }
                && javaFiles.none { it.readText().contains("latestEffectSnapshot") }) {
            "FAIL: render planning must never perform mutable latest authored lookups"
        }
        // 20. capability requirement identity includes contract range in node identity.
        require(codec != null && codec.readText().contains("capabilityRequirementCanonical")
                && codec.readText().contains("req.capabilityId().value() + \"@\" + req.contractRange()")) {
            "FAIL: capability canonical identity = CapabilityId + ContractVersionRange (single encoder, §33)"
        }
        // 21. durable snapshot store + definition version registry exist and
        //     operate over the existing timeline_snapshot immutable rows
        //     (V1-only Flyway governance — repository-reality adapted).
        val adapterFiles = fileTree(file("../timeline-module/src/main/java/com/example/platform/timeline/adapter")) { include("*.java") }.files
        val jdbcStoreSource = adapterFiles.find { it.name == "JdbcEffectSemanticSnapshotStore.java" }?.readText()
        require(jdbcStoreSource != null && jdbcStoreSource.contains("timeline_snapshot")
                && adapterFiles.any { it.name == "JdbcEffectDefinitionVersionRegistry.java" }) {
            "FAIL: durable JDBC snapshot store + definition version registry required (restart-safe invariants)"
        }
        // 22. repository V1-only Flyway governance must be preserved — no new
        //     V2 migration introduced by this workstream.
        val migrationDir = file("../platform-app/src/main/resources/db/migration")
        require(migrationDir.listFiles()?.none { it.name.startsWith("V2") } == true) {
            "FAIL: V1-only Flyway governance must be preserved (no V2 migration)"
        }

        println("OK: ROADMAP20 C20 RenderPlan boundary guard passed (provider-neutral, kernel-bound, R2 B1/B2/B3, R3 B1/B2/M1, R4 A/B/M, R5 A/B/E/F, R6 A/B/C/D/F/H, FINAL snapshot pin, ${javaFiles.size} files)")
    }
}
