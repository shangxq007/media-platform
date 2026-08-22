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
}
