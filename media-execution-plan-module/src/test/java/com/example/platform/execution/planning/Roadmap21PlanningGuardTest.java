package com.example.platform.execution.planning;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 structural guards — shadow authority / runtime boundary / CLEAN
 * FORWARD invariants (contract C22-C24 + §17 zero-count matrix).
 */
class Roadmap21PlanningGuardTest {

    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir"));
        while (p != null && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root not found");
        }
        return p;
    }

    private static boolean rootIsWorktree() {
        return repoRoot().toString().contains("/.worktrees/");
    }

    /** All production sources under the module (worktree-aware). */
    private static List<Path> moduleMain() throws IOException {
        List<Path> out = new ArrayList<>();
        Path root = repoRoot();
        boolean wt = rootIsWorktree();
        try (var walk = Files.walk(root.resolve("media-execution-plan-module/src/main/java"))) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .forEach(out::add);
        }
        return out;
    }

    /** All production sources in the #21 planning package. */
    private static List<Path> planningPackage() throws IOException {
        return moduleMain().stream()
                .filter(f -> f.toString().contains("/execution/planning/"))
                .toList();
    }

    private static String join(List<Path> files) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path f : files) {
            sb.append(Files.readString(f)).append('\n');
        }
        return sb.toString();
    }

    private static int countDefs(String src, String name) {
        int n = 0;
        for (String line : src.split("\n")) {
            String t = line.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            if (t.matches("(public\\s+)?(record|class|enum|interface|sealed interface)\\s+" + name + "\\b.*")) {
                n++;
            }
        }
        return n;
    }

    private static String stripComments(String src) {
        // remove block comments (incl. javadoc) and line comments — guards must
        // scan CODE only, not descriptive comments that mention forbidden words
        String s = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("(?m)//.*$", " ");
        return s;
    }

    // ---------- shadow authorities ----------

    @Test
    void shadowAuthoritiesAreAbsent() throws IOException {
        String src = join(moduleMain());
        assertEquals(0, countDefs(src, "ExecutionCapabilityRequirement"),
                "SHADOW_EXECUTION_CAPABILITY_REQUIREMENT_COUNT=0");
        assertEquals(0, countDefs(src, "MediaOperation"),
                "SHADOW_EXECUTION_OPERATION_AUTHORITY_COUNT=0");
        assertEquals(0, countDefs(src, "TimelineToExecutionPlanCompiler"),
                "DIRECT_TIMELINE_TO_EXECUTION_PLAN_COMPILER_COUNT=0");
        assertEquals(0, countDefs(src, "ExecutionInputRole"),
                "EXECUTION_INPUT_ROLE_SHADOW_AUTHORITY_COUNT=0");
        assertEquals(0, countDefs(src, "ExecutionOutputRole"),
                "EXECUTION_OUTPUT_ROLE_SHADOW_AUTHORITY_COUNT=0");
        assertEquals(0, countDefs(src, "ExecutionPlanErrorCode"),
                "no second failure-code authority");
        assertEquals(0, countDefs(src, "ExecutionDependencyType"),
                "GENERIC_EXECUTION_DEPENDENCY_AUTHORITY_COUNT=0");
        assertEquals(0, countDefs(src, "ExecutionDeterminism"),
                "EXECUTION_DETERMINISM_INDEPENDENT_AUTHORITY_COUNT=0");
        assertEquals(0, countDefs(src, "MediaExecutionPlan"),
                "EXECUTION_PLAN_DUAL_AUTHORITY_COUNT=0 (old plan model removed)");
        assertEquals(0, countDefs(src, "MediaExecutionStep"),
                "old step model removed");
        assertEquals(0, countDefs(src, "ExecutionStepKind"),
                "EXECUTION_STEP_KIND_INDEPENDENT_AUTHORITY_COUNT=0");
    }

    // ---------- typed preservation (Blocker A) ----------

    @Test
    void logicalNodePreservesTypedRenderNodeKind() throws IOException {
        String src = stripComments(join(planningPackage()));
        assertTrue(src.contains("RenderNodeKind sourceRenderNodeKind"),
                "RENDER_NODE_KIND_TYPED_PRESERVATION=YES — logical node must carry typed RenderNodeKind");
        assertFalse(src.matches("(?s).*String sourceRenderNodeKind.*"),
                "no String-typed sourceRenderNodeKind");
    }

    @Test
    void noStringKeyedRequirementReferences() throws IOException {
        String src = stripComments(join(planningPackage()));
        assertFalse(src.contains("List<String> outputRequirementSourceNodeIds"),
                "no string-keyed output requirement loss (Blocker A)");
        assertFalse(src.contains("List<String> materializationRequirementSourceNodeIds"),
                "no string-keyed materialization loss (Blocker A)");
        assertTrue(src.contains("RenderComponentPath componentPath"),
                "LOGICAL_COMPONENT_PATH_PRESERVED=YES");
        assertTrue(src.contains("RenderSampleWindow requiredSampleWindow"),
                "LOGICAL_SAMPLE_WINDOW_SEMANTICS_PRESERVED=YES");
    }

    // ---------- runtime boundary (Blocker F / C22) ----------

    @Test
    void noRuntimeOrProviderBindingInPlanningPackage() throws IOException {
        String src = stripComments(join(planningPackage()));
        for (String forbidden : List.of("ExecutionProvider", "providerId", "workerId", "gpuId",
                "machineId", "podId", "queueDepth", "utilization", "availability", "probeResult")) {
            assertFalse(src.contains(forbidden),
                    "planning package must not reference runtime/binding concept: " + forbidden);
        }
    }

    @Test
    void noMutableRuntimeReads() throws IOException {
        String src = stripComments(join(planningPackage()));
        assertFalse(src.contains("System.currentTimeMillis"), "no wall-clock");
        assertFalse(src.contains("Math.random"), "no random");
        assertFalse(src.contains(".now()"), "no clock reads");
        assertFalse(src.contains("Instant.now"), "no instant reads");
    }

    @Test
    void noRuntimeFailurePolicyInPlanning() throws IOException {
        String src = stripComments(join(planningPackage()));
        assertFalse(src.contains("ExecutionStepFailurePolicy"),
                "ROADMAP21_RUNTIME_FAILURE_POLICY_COUNT=0 in planning package");
        assertFalse(src.contains("FAIL_PLAN"), "no runtime failure policy active surface");
    }

    @Test
    void deferredTypesExcludedFromPlanningAndDigests() throws IOException {
        String src = stripComments(join(planningPackage()));
        for (String deferred : List.of("ExecutionResourceRequirement", "CpuClass", "MemoryClass",
                "NetworkRequirement", "TemporaryStorageClass", "ExecutionStepFailurePolicy",
                "ExecutionProvider", "ExecutionCacheKey")) {
            assertFalse(src.contains(deferred),
                    "DEFER_TO_22_PLUS type must not participate in #21 planning/digest: " + deferred);
        }
    }

    @Test
    void noFloatTimeAuthority() throws IOException {
        String src = stripComments(join(planningPackage()));
        assertFalse(src.contains("double "), "no float/double time authority");
        assertFalse(src.contains("float "), "no float time authority");
    }

    @Test
    void noCompatibilityWrappersOrDeprecatedSurface() throws IOException {
        String src = join(moduleMain());
        assertEquals(0, src.chars().filter(ch -> ch == '@').mapToObj(c -> "").count() > 0
                        && src.contains("@Deprecated") ? 1 : 0,
                "EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0 — no @Deprecated compatibility surface");
        assertFalse(src.contains("@Deprecated"),
                "EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0");
    }

    @Test
    void noInventedResourceRequirements() throws IOException {
        String planning = stripComments(join(planningPackage()));
        assertFalse(planning.contains("minimumCpuCores"), "ROADMAP21_INVENTED_RESOURCE_REQUIREMENT_COUNT=0");
        assertFalse(planning.contains("minimumMemoryBytes"), "ROADMAP21_INVENTED_RESOURCE_REQUIREMENT_COUNT=0");
        assertFalse(planning.contains("GpuRequirement"), "no invented GPU requirement in planning");
    }

    @Test
    void noPlannerInventedCapabilityOrBarrier() throws IOException {
        String planning = stripComments(join(planningPackage()));
        assertFalse(planning.contains("productType") && planning.contains("capability"),
                "PLANNER_INVENTED_CAPABILITY_REQUIREMENT_COUNT=0");
        assertFalse(planning.contains("BARRIER") || planning.contains("Barrier"),
                "PLANNER_INVENTED_BARRIER_COUNT=0");
    }

    // ---------- frozen ledger REUSE_AS_CANONICAL presence ----------

    @Test
    void reuseCanonicalStrongTypesPresentAndUsed() throws IOException {
        String src = stripComments(join(moduleMain()));
        assertEquals(1, countDefs(src, "ExecutionPlanId"), "REUSE_EXECUTION_PLAN_ID_PRESENT=1");
        assertEquals(1, countDefs(src, "ExecutionPlanSchemaVersion"), "REUSE_EXECUTION_PLAN_SCHEMA_VERSION_PRESENT=1");
        assertEquals(1, countDefs(src, "ExecutionEdgeId"), "REUSE_EXECUTION_EDGE_ID_PRESENT=1");
        assertEquals(1, countDefs(src, "ExecutionInputId"), "REUSE_EXECUTION_INPUT_ID_PRESENT=1");
        assertEquals(1, countDefs(src, "ExecutionOutputId"), "REUSE_EXECUTION_OUTPUT_ID_PRESENT=1");
        assertEquals(1, countDefs(src, "ExecutionStepId"), "REUSE_EXECUTION_STEP_ID_PRESENT=1");
        assertEquals(1, countDefs(src, "ExecutionCreationContext"), "REUSE_EXECUTION_CREATION_CONTEXT_PRESENT=1");
        // strong types actually used in the planning model
        String planning = stripComments(join(planningPackage()));
        assertTrue(planning.contains("ExecutionPlanId planId") || planning.contains("ExecutionPlanId planId"),
                "ExecutionPlanId used in PhysicalExecutionPlan");
        assertTrue(planning.contains("ExecutionInputId inputId"), "ExecutionInputId used in InputBinding");
        assertTrue(planning.contains("ExecutionOutputId outputId"), "ExecutionOutputId used in OutputDeclaration");
        assertTrue(planning.contains("ExecutionStepId stepId"), "ExecutionStepId used in PhysicalPlanUnit");
    }

    @Test
    void executionPlanIdNotDerivedFromFingerprint() throws IOException {
        // ExecutionPlanId must be caller-supplied planning input, never
        // re-derived from semantic fingerprint/content hash
        String planner = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalPlannerV1.java")));
        assertTrue(planner.contains("planId") && planner.contains("ExecutionPlanId planId"),
                "ExecutionPlanId passed in as explicit planner input");
        assertFalse(planner.matches("(?s).*new ExecutionPlanId\\([^;]*sha256[^;]*\\);.*"),
                "ExecutionPlanId must NOT be derived from a sha256 of semantic content");
        assertFalse(planner.matches("(?s).*new ExecutionPlanId\\([^)]*fingerprint[^)]*\\).*"),
                "ExecutionPlanId must NOT be derived from plan fingerprint");
    }

    // ---------- C12/C13 coordinate-domain guards ----------

    @Test
    void noDirectSampleWindowVsExtentComparison() throws IOException {
        String planning = stripComments(join(planningPackage()));
        // pruning must reference executionCoverage, never requiredSampleWindow
        assertTrue(planning.contains("coverageDisjointFromExtent"),
                "pruning uses typed execution coverage");
        assertFalse(planning.contains("windowDisjointFromExtent"),
                "DIRECT_RENDER_SAMPLE_WINDOW_VS_RENDER_EXTENT_COMPARISON_COUNT=0 — old window-based pruning removed");
        // mechanical: no expression mixing requiredSampleWindow with
        // requestedExtent (direct comparison in any form)
        assertFalse(java.util.regex.Pattern.compile(
                        "requiredSampleWindow[\\s\\S]{0,120}requestedExtent")
                        .matcher(planning).find(),
                "DIRECT_RENDER_SAMPLE_WINDOW_VS_RENDER_EXTENT_COMPARISON_COUNT=0 — no sample-window/extent comparison expression");
    }

    @Test
    void noAllProducersEliminatedPruning() throws IOException {
        String planning = stripComments(join(planningPackage()));
        assertFalse(planning.contains("ALL_PRODUCERS_ELIMINATED"),
                "ALL_PRODUCERS_ELIMINATED_PRUNING=FORBIDDEN");
        assertFalse(planning.contains("allProducersEliminated"),
                "no producer-elimination inference");
    }

    @Test
    void noObjectToStringCanonicalSemanticUsage() throws IOException {
        String planning = stripComments(join(planningPackage()));
        // Canonical must not rely on Object.toString as semantic contract for
        // the types it explicitly encodes
        String canonical = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/Canonical.java")));
        assertFalse(canonical.contains("dependencyVariant().toString()"),
                "OBJECT_TOSTRING_CANONICAL_SEMANTIC_USAGE_COUNT=0 — dependency uses explicit encoding");
        assertFalse(canonical.contains("return d.toString()"),
                "OBJECT_TOSTRING_CANONICAL_SEMANTIC_USAGE_COUNT=0 — dependency encoding must not delegate to Object.toString");
        assertFalse(canonical.contains("capability(cr)") && canonical.contains("cr.toString()"),
                "capability encoding explicit (no toString semantic authority)");
    }


    // ---------- Correction 3 guards (B2/B3) ----------

    @Test
    void noRenderNodeCompatibilitySurface() throws IOException {
        // RAW text (comments included): a compatibility constructor/factory
        // declaration is surface evidence even in javadoc form
        String rn = Files.readString(repoRoot().resolve(
                "render-module/src/main/java/com/example/platform/render/domain/renderplan/RenderNode.java"));
        assertFalse(rn.contains("Compatibility constructor"),
                "RENDER_NODE_COMPATIBILITY_CONSTRUCTOR_COUNT=0");
        assertFalse(rn.contains("Backwards-compatible"),
                "RENDER_NODE_BACKWARDS_COMPATIBLE_FACTORY_COUNT=0");
        // exactly one canonical constructor (the record canonical ctor)
        assertTrue(rn.contains("public record RenderNode("), "canonical record ctor present");
        assertFalse(rn.contains("public RenderNode(\n            RenderNodeId id,\n            RenderNodeKind kind,\n            RenderComponentPath componentPath,\n            String operationKey,\n            List<RenderArtifactReference> artifactReferences,\n            List<CapabilityRequirement> capabilityRequirements,\n            List<RenderOutputRequirement> outputRequirements,\n            List<RenderExecutionRequirement> executionRequirements,\n            List<RenderMaterializationRequirement> materializationRequirements,\n            Optional<RenderSampleWindow> requiredSampleWindow) {"),
                "no 10-arg compatibility constructor body remains");
    }

    @Test
    void noStringLogicalEdgeIdAuthority() throws IOException {
        String planning = stripComments(join(planningPackage()));
        assertFalse(planning.contains("String edgeId,"),
                "STRING_LOGICAL_EDGE_ID_AUTHORITY_COUNT=0 — LogicalDependencyEdge uses ExecutionEdgeId");
        assertTrue(planning.contains("ExecutionEdgeId edgeId,"),
                "EXECUTION_EDGE_ID_TYPED_USAGE=YES");
    }

    @Test
    void noSchemaVersionMajorMinorRedesign() throws IOException {
        String sv = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/domain/ExecutionPlanSchemaVersion.java")));
        assertFalse(sv.contains("major") || sv.contains("minor"),
                "SCHEMA_VERSION_MAJOR_MINOR_REDESIGN_COUNT=0 — frozen int value semantics");
        assertTrue(sv.contains("int value") && sv.contains("value < 1"),
                "EXECUTION_SCHEMA_VERSION_EXACT_FROZEN_SEMANTICS=YES (int value >= 1)");
    }

    @Test
    void executionCreationContextFrozenShape() throws IOException {
        String cc = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/domain/ExecutionCreationContext.java")));
        for (String field : new String[]{"requestedByUserId", "requestedByTenantId", "requestPurpose",
                "createdAt", "traceId", "parentPlanId", "comment"}) {
            assertTrue(cc.contains(field), "EXECUTION_CREATION_CONTEXT_FROZEN_SHAPE=YES — field " + field);
        }
    }

    @Test
    void noAllProducersEliminatedPruningCount() throws IOException {
        String planning = stripComments(join(planningPackage()));
        assertFalse(planning.contains("allProducersEliminated") || planning.contains("ALL_PRODUCERS_ELIMINATED"),
                "ALL_PRODUCERS_ELIMINATED_PRUNING_COUNT=0");
        // node elimination may ONLY happen via the node's OWN typed coverage
        // (deterministic coverage loop) — never via producer-elimination inference
        String builder = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/LogicalExecutionGraphBuilder.java")));
        long eliminationSites = java.util.regex.Pattern.compile("eliminated\\.add\\(").matcher(builder).results().count();
        assertEquals(1, eliminationSites,
                "ALL_PRODUCERS_ELIMINATED_PRUNING_COUNT=0 — the ONLY node-elimination site is coverage-based");
    }


    // ---------- Correction 4 guards ----------

    @Test
    void timedTextCoverageNeverNullInMaterializer() throws IOException {
        String mat = stripComments(Files.readString(repoRoot().resolve(
                "render-module/src/main/java/com/example/platform/render/domain/renderplan/DefaultRenderMaterializer.java")));
        // TIMED_TEXT coverage assignment present; NO null-coverage path for
        // the TIMED_TEXT node construction
        assertTrue(mat.contains("textCoverage = new RenderExecutionCoverage"),
                "TIMED_TEXT_NULL_EXECUTION_COVERAGE_IN_MATERIALIZER_COUNT=0 — materializer assigns exact coverage");
        assertTrue(mat.contains("List.of(textRequirement), Optional.empty(), textCoverage);"),
                "TIMED_TEXT_NULL_EXECUTION_COVERAGE_IN_MATERIALIZER_COUNT=0 — "
                        + "TIMED_TEXT node construction uses the exact projected coverage (never null)");
        assertTrue(mat.contains("ExactTextTimelineTimeProjection"),
                "exact #20-owned projection used");
        // no float/double/millisecond text-time conversion in the BRIDGE
        String projection = stripComments(Files.readString(repoRoot().resolve(
                "render-module/src/main/java/com/example/platform/render/domain/renderplan/ExactTextTimelineTimeProjection.java")));
        assertFalse(projection.contains("doubleValue()") || projection.contains("toDouble")
                        || projection.contains("BigDecimal") || projection.contains("toMillis"),
                "TIMED_TEXT_FLOAT_TIME_CONVERSION_COUNT=0 — bridge is exact rational only");
        assertFalse(projection.contains("double") || projection.contains("float"),
                "no float/double in the exact projection");
    }

    @Test
    void schemaVersionFrozenV1ConstantPresent() throws IOException {
        String sv = Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/domain/ExecutionPlanSchemaVersion.java"));
        assertTrue(sv.contains("V1 = new ExecutionPlanSchemaVersion(1)"),
                "EXECUTION_SCHEMA_VERSION_V1_CONSTANT_MISSING_COUNT=0 — V1 constant present");
        assertTrue(sv.contains("implements Serializable"), "frozen Serializable surface");
        assertTrue(sv.contains("public static ExecutionPlanSchemaVersion of(int value)"),
                "frozen of(int) factory");
        assertFalse(sv.contains("int major") || sv.contains("int minor"),
                "SCHEMA_VERSION_MAJOR_MINOR_REDESIGN_COUNT=0");
    }

    @Test
    void creationContextFrozenShapeAndInvariants() throws IOException {
        String cc = Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/domain/ExecutionCreationContext.java"));
        assertTrue(cc.contains("String parentPlanId"),
                "EXECUTION_CREATION_CONTEXT_PARENT_PLAN_ID_NONFROZEN_TYPE_COUNT=0 — parentPlanId is String");
        assertFalse(cc.contains("ExecutionPlanId parentPlanId"),
                "parentPlanId must remain frozen String (not ExecutionPlanId)");
        assertTrue(cc.contains("Objects.requireNonNull(createdAt"),
                "EXECUTION_CREATION_CONTEXT_NULLABLE_CREATED_AT_COUNT=0 — createdAt required");
        assertTrue(cc.contains("implements Serializable"), "frozen Serializable surface");
        for (String f : new String[]{"requestedByUserId", "requestedByTenantId", "requestPurpose",
                "Instant createdAt", "traceId", "String parentPlanId", "comment"}) {
            assertTrue(cc.contains(f), "frozen field " + f);
        }
    }

    @Test
    void noDelimiterOnlyFramingForFreeStrings() throws IOException {
        String canonical = Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/Canonical.java"));
        assertTrue(canonical.contains("framed("),
                "DELIMITER_ONLY_CANONICAL_SCALAR_ENCODING_COUNT=0 — length-prefixed framing used");
        String code = canonical.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        // effect parameter values / automation references / ids must be framed
        assertFalse(code.contains("p.value() != null ? p.value() : \"null\") + \"=\"") && !code.contains("framed(p.value()"),
                "free-string scalars must be length-prefixed");
    }

    @Test
    void physicalUnitExtentParticipatesInDigest() throws IOException {
        String pd = Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalExecutionPlanDigest.java"));
        assertTrue(pd.contains("unitExtent"),
                "PHYSICAL_UNIT_PROPAGATED_EXTENT_DIGEST_OMISSION_COUNT=0 — unit propagatedExtent in digest");
        assertTrue(pd.contains("u.propagatedExtent()"), "digest reads unit propagatedExtent");
    }


    // ---------- Correction 5 guards ----------

    @Test
    void noProductionFontTextDependency() throws IOException {
        String build = Files.readString(repoRoot().resolve(
                "media-execution-plan-module/build.gradle.kts"));
        assertFalse(build.contains("implementation(project(\":font-text-module\"))"),
                "ROADMAP_21_PRODUCTION_FONT_TEXT_DEPENDENCY_COUNT=0 — no production font-text dependency");
        assertTrue(build.contains("testImplementation(project(\":font-text-module\"))"),
                "font-text only testImplementation (T2 bridge test fixtures)");
        // zero FontRational references in #21 production sources
        try (var walk = java.nio.file.Files.walk(repoRoot().resolve(
                "media-execution-plan-module/src/main"))) {
            long refs = walk.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> {
                        try { return java.nio.file.Files.readString(f).contains("FontRational"); }
                        catch (java.io.IOException e) { return false; }
                    }).count();
            assertEquals(0, refs,
                    "ROADMAP_21_PRODUCTION_FONTRATIONAL_REFERENCE_COUNT=0");
        }
    }

    @Test
    void reuseTypesFrozenSignatures() throws IOException {
        String domain = repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/domain").toString();
        for (String t : new String[]{"ExecutionPlanId", "ExecutionEdgeId", "ExecutionInputId",
                "ExecutionOutputId", "ExecutionStepId"}) {
            String src = Files.readString(java.nio.file.Path.of(domain, t + ".java"));
            assertTrue(src.contains("implements Serializable"), t + " Serializable");
            assertTrue(src.contains("value == null || value.isBlank()"), t + " frozen null/blank check");
            assertTrue(src.contains("IllegalArgumentException"), t + " frozen exception type");
            assertTrue(src.contains("return value;"), t + " frozen toString=value");
        }
        String cc = Files.readString(java.nio.file.Path.of(domain, "ExecutionCreationContext.java"));
        assertTrue(cc.contains("withTraceId"), "frozen withTraceId present");
        assertTrue(cc.contains("withComment"), "frozen withComment present");
        assertTrue(cc.contains("creationCtx{"), "frozen explicit toString present");
        assertFalse(cc.contains("getRequestPurpose"),
                "non-frozen public drift removed (no getRequestPurpose in frozen 99aa4162)");
        assertFalse(cc.contains("absent()"), "Correction-2 invented absent() removed");
        String sv = Files.readString(java.nio.file.Path.of(domain, "ExecutionPlanSchemaVersion.java"));
        assertTrue(sv.contains("V1 = new ExecutionPlanSchemaVersion(1)"), "frozen V1");
        assertTrue(sv.contains("of(int value)"), "frozen of(int)");
    }

    @Test
    void noRawOuterDelimiterGrammarInDigests() throws IOException {
        for (String f : new String[]{"LogicalExecutionGraphDigest.java", "PhysicalExecutionPlanDigest.java"}) {
            String src = Files.readString(repoRoot().resolve(
                    "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/" + f));
            assertTrue(src.contains("CanonicalWriter"),
                    f + " uses the framed canonical writer");
            String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
            // no raw append('|') / append("|") delimiter grammar in digest encoders
            assertFalse(code.contains("append('|')") || code.contains("append(\"|\")"),
                    f + " LOGICAL_OUTER_DELIMITER_GRAMMAR_REMOVED — framed writer only");
        }
    }


    // ---------- Correction 6 guards ----------

    @Test
    void logicalDigestEdgeOrderNonSemantic() throws IOException {
        String lg = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/LogicalExecutionGraphDigest.java")));
        assertTrue(lg.contains("CanonicalWriter.sorted(edgeCanonicals)"),
                "LOGICAL_EDGE_ORDER_SEMANTIC_PRESERVE_ASSUMPTION_COUNT=0 — edges canonical-sorted (upstream #20 sorts edge encodings)");
        assertFalse(lg.contains("String.join"),
                "LOGICAL_DIGEST_RAW_ELIMINATED_STRING_JOIN_COUNT=0 — no delimiter join of semantic collections");
    }

    @Test
    void physicalDigestCollectionsCanonicalSorted() throws IOException {
        String pd = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalExecutionPlanDigest.java")));
        assertTrue(pd.contains("CanonicalWriter.sorted(inputCanonicals)"), "inputs sorted (upstream sorts artifacts)");
        assertTrue(pd.contains("CanonicalWriter.sorted(outputCanonicals)"), "outputs sorted (upstream sorts outputs)");
        assertTrue(pd.contains("CanonicalWriter.sorted(depCanonicals)"), "dependencies sorted (edge set non-semantic)");
    }

    @Test
    void guardedEntryIsSoleProductionPath() throws IOException {
        // ExecutionPlanningEntry is the guarded production boundary; no other
        // production source may invoke LogicalPhysicalPlanner directly
        String mep = repoRoot().resolve(
                "media-execution-plan-module/src/main").toString();
        int plannerCalls = 0;
        try (var walk = java.nio.file.Files.walk(java.nio.file.Path.of(mep))) {
            for (var f : (Iterable<java.nio.file.Path>) walk.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String src = Files.readString(f);
                if (src.contains("LogicalPhysicalPlanner.plan(")
                        && !f.toString().endsWith("ExecutionPlanningEntry.java")) {
                    plannerCalls++;
                }
            }
        }
        assertEquals(0, plannerCalls,
                "PRODUCTION_DIRECT_LOGICAL_PHYSICAL_PLANNER_CALLERS_OUTSIDE_GUARDED_ENTRY=0");
        String entry = Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/ExecutionPlanningEntry.java"));
        assertTrue(entry.contains("RENDER_PLANNING_RESULT_NOT_PLANNABLE"),
                "guarded entry rejects with typed reason");
        assertTrue(entry.contains("RenderPlanStatus.PLANNABLE"), "only PLANNABLE accepted");
    }

    @Test
    void pruningEvidenceDocCanonical() throws IOException {
        String lg = Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/LogicalExecutionGraph.java"));
        assertFalse(lg.contains("required sample window is fully disjoint"),
                "STALE_SAMPLE_WINDOW_PRUNING_DOC_REMOVED — PruningEvidence javadoc corrected");
        assertFalse(lg.contains("all of its producers were eliminated"),
                "ALL_PRODUCERS_ELIMINATED_DOC_REMOVED — stale transitive inference doc removed");
        assertTrue(lg.contains("OWN typed RenderExecutionCoverage"),
                "COVERAGE_VS_EXTENT_DOC_CANONICAL — coverage vs extent wording canonical");
        assertTrue(lg.contains("NEVER participates in extent-pruning"),
                "RenderSampleWindow excluded from pruning doc");
    }

}
