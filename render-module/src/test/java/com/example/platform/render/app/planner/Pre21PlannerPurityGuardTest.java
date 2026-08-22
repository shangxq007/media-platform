package com.example.platform.render.app.planner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRE-#21 structural guard — planner purity (C1/C2/C3).
 *
 * Mechanically proves the logical planner has ZERO mutable-runtime
 * dependencies:
 *
 *   LOGICAL_PLANNER_RUNTIME_MUTABLE_READ_COUNT = 0
 *   LOGICAL_PLANNING_RUNTIME_INFRASTRUCTURE_LEAK_COUNT = 0
 *
 * Forbidden dependencies (constructor/field/import) in the planner package:
 *   ProductRuntimeService, ProducerRuntimeService, CapabilityResolutionService,
 *   and any runtime registry/service-locator type.
 *
 * Symbol-set bounded; false-positive resistant (test files excluded).
 */
class Pre21PlannerPurityGuardTest {

    private static final String PLANNER_PACKAGE = "com.example.platform.render.app.planner";

    private static final List<String> FORBIDDEN_RUNTIME_SYMBOLS = List.of(
            "ProductRuntimeService",
            "ProducerRuntimeService",
            "CapabilityResolutionService",
            "ExecutionBackendRegistry",
            "ServiceLocator");

    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir"));
        while (p != null && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return p;
    }

    private static List<Path> plannerProductionFiles() throws IOException {
        Path root = repoRoot();
        boolean rootIsWorktree = root.toString().contains("/.worktrees/");
        Path worktreesDir = rootIsWorktree
                ? root.getParent().getParent().resolve(".worktrees")
                : root.resolve(".worktrees");
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().contains("/render-module/src/main/java/"))
                    .filter(f -> f.toString().contains("/app/planner/"))
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.getFileName().toString().equals("CapabilityResolutionService.java"))
                    .filter(f -> !f.startsWith(worktreesDir) || (rootIsWorktree && f.startsWith(root)))
                    .toList();
        }
    }

    @Test
    void plannerHasNoMutableRuntimeDependency() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : plannerProductionFiles()) {
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                    continue;
                }
                for (String sym : FORBIDDEN_RUNTIME_SYMBOLS) {
                    if (t.contains(sym)) {
                        violations.add(f.getFileName() + ":" + (i + 1) + ": " + t);
                    }
                }
            }
        }
        assertEquals(List.of(), violations,
                "LOGICAL_PLANNER_RUNTIME_MUTABLE_READ_COUNT must be 0 — planner package must not "
                        + "reference mutable runtime services");
    }

    @Test
    void plannerDependsOnlyOnFrozenContextAndDomain() throws IOException {
        // Constructor/field surface must not include runtime service types.
        for (Path f : plannerProductionFiles()) {
            String c = Files.readString(f);
            for (String sym : FORBIDDEN_RUNTIME_SYMBOLS) {
                assertTrue(!c.contains("private final " + sym + " ")
                                && !c.contains("public " + sym + "("),
                        f.getFileName() + " must not hold " + sym + " as field/ctor dependency");
            }
        }
    }

    @Test
    void frozenPlanningContextExistsAndIsImmutable() throws IOException {
        Path ctx = repoRoot().resolve("render-module/src/main/java/com/example/platform/render/app/planner/FrozenPlanningContext.java");
        assertTrue(Files.exists(ctx), "FrozenPlanningContext must exist (C2)");
        String c = Files.readString(ctx);
        assertTrue(c.contains("public record FrozenPlanningContext("), "must be an immutable record");
        assertTrue(c.contains("Map.copyOf"), "must freeze maps at construction");
    }
}
