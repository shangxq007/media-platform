package com.example.platform.render.app.planner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R21E C-01 — task handler single authority guard.
 *
 * ONE_TASK_CAPABILITY_ONE_HANDLER_AUTHORITY_V1:
 * - no production Mock ASR/PROBE handler definitions (mock execution is
 *   log-only and must never become semantic execution authority)
 * - no silent duplicate overwrite in TaskHandlerRegistry (put → must be
 *   putIfAbsent with fail-closed duplicate rejection)
 * - no order-based authority (no @Order on TaskHandler beans, no sorting)
 * - exactly one production ASR handler; the render-owned PROBE handler is
 *   intentionally absent after the Phase 19 clean-forward migration
 */
class Roadmap21EntryResidualGuardTest {

    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir"));
        while (p != null && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root not found from " + System.getProperty("user.dir"));
        }
        return p;
    }

    private static List<Path> productionJavaFiles() throws IOException {
        List<Path> out = new ArrayList<>();
        Path root = repoRoot();
        boolean rootIsWorktree = root.toString().contains("/.worktrees/");
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.toString().contains("/.worktrees/")
                            || (rootIsWorktree && f.toString().startsWith(root.toString())))
                    .filter(f -> !f.toString().contains("/.git/"))
                    .forEach(out::add);
        }
        return out;
    }

    @Test
    void noProductionMockAsrOrProbeHandlerDefinitions() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.equals("MockAsrTaskHandler.java") || name.equals("MockProbeTaskHandler.java")) {
                violations.add(f.toString());
            }
        }
        assertEquals(List.of(), violations,
                "PRODUCTION_MOCK_ASR_HANDLER_DEFINITION_COUNT=0 and "
                        + "PRODUCTION_MOCK_PROBE_HANDLER_DEFINITION_COUNT=0 required");
    }

    @Test
    void exactlyOneAsrAndNoRenderOwnedProbeProductionHandler() throws IOException {
        int asr = 0;
        int probe = 0;
        for (Path f : productionJavaFiles()) {
            String c = Files.readString(f);
            if (c.contains("implements TaskHandler")) {
                if (c.contains("return TaskCapability.ASR;") || c.contains("return TaskCapability.ASR")) {
                    asr++;
                }
                if (c.contains("return TaskCapability.PROBE;") || c.contains("return TaskCapability.PROBE")) {
                    probe++;
                }
            }
        }
        assertEquals(1, asr, "ASR_ACTIVE_HANDLER_COUNT must be exactly 1");
        assertEquals(0, probe,
                "PROBE_ACTIVE_HANDLER_COUNT must be 0 after render-owned media probe authority removal");
    }

    @Test
    void registryIsFailClosedNoSilentOverwrite() throws IOException {
        Path registry = repoRoot().resolve(
                "outbox-event-module/src/main/java/com/example/platform/outbox/coordination/TaskHandlerRegistry.java");
        assertTrue(Files.exists(registry), "TaskHandlerRegistry must exist");
        String c = Files.readString(registry);
        assertTrue(c.contains("putIfAbsent"), "registry must use putIfAbsent (TASK_HANDLER_SILENT_DUPLICATE_OVERWRITE_COUNT=0)");
        assertFalse(c.contains("handlers.put(handler.capability(), handler)"),
                "silent last-write-wins overwrite forbidden");
        assertTrue(c.contains("IllegalStateException"),
                "duplicate capability registration must fail closed");
    }

    @Test
    void noOrderBasedTaskHandlerAuthority() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String c = Files.readString(f);
            if (c.contains("implements TaskHandler")
                    && (c.contains("@Order") || c.contains("Comparator.comparing"))) {
                violations.add(f.toString());
            }
        }
        assertEquals(List.of(), violations,
                "TASK_HANDLER_ORDER_BASED_AUTHORITY_COUNT=0 required");
    }
}
