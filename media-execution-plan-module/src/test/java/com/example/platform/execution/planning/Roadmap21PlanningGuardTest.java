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
 * FORWARD invariants (contract C22-C24, disposition ledger).
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

    private static List<Path> moduleMain() throws IOException {
        List<Path> out = new ArrayList<>();
        try (var walk = Files.walk(repoRoot().resolve("media-execution-plan-module/src/main/java"))) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .forEach(out::add);
        }
        return out;
    }

    private static String moduleSource() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path f : moduleMain()) {
            sb.append(Files.readString(f)).append('\n');
        }
        return sb.toString();
    }

    @Test
    void shadowAuthoritiesAreAbsent() throws IOException {
        String src = moduleSource();
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
        assertEquals(0, countDefs(src, "GpuRequirement"),
                "no invented GPU requirement (single authority: RenderExecutionRequirement.gpu)");
    }

    @Test
    void noIndependentStepKindOrDependencyOrDeterminismAuthority() throws IOException {
        String src = moduleSource();
        assertEquals(0, countDefs(src, "ExecutionStepKind"),
                "EXECUTION_STEP_KIND_INDEPENDENT_AUTHORITY_COUNT=0");
        assertEquals(0, countDefs(src, "ExecutionDependencyType"),
                "GENERIC_EXECUTION_DEPENDENCY_AUTHORITY_COUNT=0");
        assertEquals(0, countDefs(src, "ExecutionDeterminism"),
                "EXECUTION_DETERMINISM_INDEPENDENT_AUTHORITY_COUNT=0");
    }

    @Test
    void noRuntimeFailurePolicyInPlanningPackage() throws IOException {
        // ExecutionStepFailurePolicy may exist as a DEFER_TO_22_PLUS surface in
        // the domain package, but MUST NOT participate in the #21 planning package.
        Path planning = repoRoot().resolve("media-execution-plan-module/src/main/java/com/example/platform/execution/planning");
        try (var walk = Files.walk(planning)) {
            var files = walk.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".java")).toList();
            for (Path f : files) {
                String c = Files.readString(f);
                assertFalse(c.contains("ExecutionStepFailurePolicy"),
                        "ROADMAP21_RUNTIME_FAILURE_POLICY_COUNT=0 in planning package (#22 runtime failure policy): " + f.getFileName());
                assertFalse(c.contains("FAIL_PLAN"),
                        "runtime failure policy must not be an active #21 surface");
            }
        }
    }

    @Test
    void noMutableRuntimeReadsOrAvailabilityAuthority() throws IOException {
        String src = moduleSource();
        assertFalse(src.contains("AVAILABLE") && src.contains("availability"),
                "EXECUTION_INPUT_MUTABLE_AVAILABILITY_AUTHORITY_COUNT=0");
        assertFalse(src.contains("System.currentTimeMillis"),
                "no wall-clock semantic input");
        assertFalse(src.contains("Math.random"),
                "no random semantic input");
        assertFalse(src.contains("providerRegistry") || src.contains("workerStatus"),
                "no runtime registry reads");
    }

    @Test
    void noCompatibilityWrappersOrDualAuthority() throws IOException {
        String src = moduleSource();
        assertEquals(0, countDefs(src, "MediaExecutionPlan"),
                "EXECUTION_PLAN_DUAL_AUTHORITY_COUNT=0 (old plan model removed)");
        assertEquals(0, countDefs(src, "MediaExecutionStep"),
                "old step model removed");
        assertFalse(src.contains("@Deprecated"),
                "EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0 (no deprecated compatibility surface)");
    }

    @Test
    void deferredTypesDoNotParticipateInPlanningDigest() throws IOException {
        // #21 planning package must not reference DEFER_TO_22_PLUS types
        Path planning = repoRoot().resolve("media-execution-plan-module/src/main/java/com/example/platform/execution/planning");
        try (var walk = Files.walk(planning)) {
            var files = walk.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".java")).toList();
            for (Path f : files) {
                String c = Files.readString(f);
                for (String deferred : List.of("ExecutionResourceRequirement", "CpuClass", "MemoryClass",
                        "NetworkRequirement", "TemporaryStorageClass", "ExecutionStepFailurePolicy",
                        "ExecutionProvider", "ExecutionCacheKey")) {
                    assertFalse(c.contains(deferred),
                            "DEFER_TO_22_PLUS type " + deferred + " must not participate in #21 planning: " + f.getFileName());
                }
            }
        }
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
}
