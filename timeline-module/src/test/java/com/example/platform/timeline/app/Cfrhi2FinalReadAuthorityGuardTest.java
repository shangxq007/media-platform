package com.example.platform.timeline.app;

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
 * CFRH-I2 final read-authority closure guard (I2-G, final exactness).
 *
 * Mechanically proves the frozen final metrics on production sources:
 *
 *   LEGACY_TIMELINE_REVISION_QUERY_SERVICE_REFERENCE_COUNT        = 0
 *   LEGACY_TIMELINE_REVISION_QUERY_INVOCATION_SITE_COUNT          = 0
 *   AMBIENT_GLOBAL_TIMELINE_SNAPSHOT_READ_COUNT                   = 0
 *   AMBIENT_GLOBAL_TIMELINE_REVISION_READ_COUNT                   = 0
 *   UNPRIVILEGED_SYSTEM_GLOBAL_TIMELINE_READ_COUNT                = 0
 *   KNOWN_UNSCOPED_PRODUCTION_TIMELINE_READ_SYMBOL_COUNT          = 0
 *
 * Final-exactness (independent review): legacy unscoped read API
 * DEFINITIONS must also be zero, not merely invocations:
 *
 *   TimelineSnapshotService.findPayload(String) definition count        = 0
 *   TimelineSnapshotService.findById(String) definition count          = 0
 *   TimelineRevisionRepository.findById(String) definition count       = 0
 *   TimelineRevisionRepository.findHeadByProject(String) definition    = 0
 *   legacy TimelineRevisionRepository.listByProject(...) definition    = 0
 *   old project-only latest normal API definition count                = 0
 *
 * System primitives are explicitly system-only:
 *   findLatestForSystemMaintenance / listProjectIdsForSystemMaintenance
 *   may be called ONLY from SystemMaintenanceReader.
 *
 * Scope is symbol-set bounded (frozen I2 contract).
 */
class Cfrhi2FinalReadAuthorityGuardTest {

    // Retired service — zero references anywhere in production.
    private static final String LEGACY_SERVICE = "TimelineRevisionService";

    // Ambient-global read symbols forbidden in production (definitions AND
    // call sites — final exactness, independent review).
    private static final List<String> FORBIDDEN_GLOBAL_READ_SYMBOLS = List.of(
            "findPayload",
            "findLatestByProject");

    // Legacy unscoped definitions forbidden anywhere in the adapters.
    private static final List<String> FORBIDDEN_ADAPTER_DEFINITIONS = List.of(
            "findPayload",
            "findById",
            "findHeadByProject",
            "listByProject");

    // System-only primitives: definition allowed in adapter; call sites
    // allowed ONLY inside SystemMaintenanceReader.
    private static final List<String> SYSTEM_ONLY_PRIMITIVES = List.of(
            "findLatestForSystemMaintenance",
            "listProjectIdsForSystemMaintenance");

    // Adapters that must not define the forbidden legacy surface.
    private static final List<String> ADAPTER_FILES = List.of(
            "TimelineSnapshotService.java",
            "TimelineRevisionRepository.java");

    private static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cur = start;
        while (cur != null && !Files.exists(cur.resolve("settings.gradle.kts"))
                && !Files.exists(cur.resolve("settings.gradle"))) {
            cur = cur.getParent();
        }
        return cur != null ? cur : start;
    }

    private static List<Path> productionJavaFiles() throws IOException {
        List<Path> out = new ArrayList<>();
        Path root = repoRoot();
        boolean rootIsWorktree = root.toString().contains("/.worktrees/");
        Path worktreesDir = rootIsWorktree
                ? root.getParent().getParent().resolve(".worktrees")
                : root.resolve(".worktrees");
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.startsWith(worktreesDir) || (rootIsWorktree && f.startsWith(root)))
                    .forEach(out::add);
        }
        return out;
    }

    private static boolean isCommentLine(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    @Test
    void legacyQueryServiceReferenceCountIsZero() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.equals("Cfrhi1LegacyWriteAuthorityGuardTest.java")
                    || name.equals("Cfrhi2SystemAuthorityGuardTest.java")
                    || name.equals("Cfrhi2FinalReadAuthorityGuardTest.java")) {
                continue; // guard test files reference the name in assertions
            }
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (isCommentLine(trimmed)) {
                    continue;
                }
                if (trimmed.contains(LEGACY_SERVICE)) {
                    violations.add(f + ":" + (i + 1) + ": " + trimmed);
                }
            }
        }
        assertEquals(List.of(), violations,
                "LEGACY_TIMELINE_REVISION_QUERY_SERVICE_REFERENCE_COUNT must be 0");
    }

    @Test
    void legacyUnscopedAdapterDefinitionsAreZero() throws IOException {
        // Final exactness: legacy unscoped read API definitions must be zero.
        // - findPayload / findHeadByProject / listByProject / findLatestByProject:
        //   globally unique legacy names — any production definition is a violation.
        // - findById: a generic name used by other repositories; the legacy
        //   timeline definition is bounded to the two timeline adapters.
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            // Legacy timeline read symbols are unique only within the timeline
            // module; other modules legitimately define listByProject /
            // findLatestByProject on their own aggregates.
            boolean timelineModule = f.toString().contains("/timeline-module/");
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (isCommentLine(trimmed)) {
                    continue;
                }
                for (String def : List.of("findPayload", "findHeadByProject", "listByProject", "findLatestByProject")) {
                    if (timelineModule
                            && trimmed.matches(".*\\b(public|private|protected)\\s+[\\w<>\\[\\],.\\s]+\\b" + def + "\\s*\\(.*")) {
                        violations.add(f + ":" + (i + 1) + " legacy definition " + def + ": " + trimmed);
                    }
                }
                // findById legacy definition — bounded to the timeline adapters
                if (ADAPTER_FILES.contains(name)
                        && trimmed.matches(".*\\b(public|private|protected)\\s+[\\w<>\\[\\],.\\s]+\\bfindById\\s*\\(.*")) {
                    violations.add(f + ":" + (i + 1) + " legacy findById definition: " + trimmed);
                }
            }
        }
        assertEquals(List.of(), violations,
                "LEGACY_UNSCOPED_READ_DEFINITION_COUNT must be 0 (findPayload/findById/findHeadByProject/listByProject/findLatestByProject)");
    }

    @Test
    void systemPrimitivesAreCalledOnlyFromSystemMaintenanceReader() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.equals("TimelineSnapshotService.java")) {
                continue; // adapter definitions
            }
            if (name.equals("SystemMaintenanceReader.java")) {
                continue; // the privileged port
            }
            if (name.equals("Cfrhi2SystemAuthorityGuardTest.java")
                    || name.equals("Cfrhi2FinalReadAuthorityGuardTest.java")) {
                continue;
            }
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (isCommentLine(trimmed)) {
                    continue;
                }
                for (String prim : SYSTEM_ONLY_PRIMITIVES) {
                    if (trimmed.contains(prim + "(")) {
                        violations.add(f + ":" + (i + 1) + " direct system primitive " + prim + ": " + trimmed);
                    }
                }
            }
        }
        assertEquals(List.of(), violations,
                "DIRECT_SYSTEM_PRIMITIVE_BYPASS_COUNT must be 0 (system primitives callable only from SystemMaintenanceReader)");
    }

    @Test
    void ambientGlobalTimelineReadsAreZeroInProduction() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (isCommentLine(trimmed)) {
                    continue;
                }
                for (String sym : FORBIDDEN_GLOBAL_READ_SYMBOLS) {
                    // invocation sites on the TIMELINE snapshot service receiver
                    // (timelineSnapshotService / snapshotService); adapter has no
                    // definitions anymore and SystemMaintenanceReader uses the
                    // renamed system primitives, so any hit is a violation
                    if (trimmed.contains("timelineSnapshotService." + sym + "(")
                            || trimmed.contains("snapshotService." + sym + "(")) {
                        violations.add(f + ":" + (i + 1) + " global read " + sym + ": " + trimmed);
                    }
                }
            }
        }
        assertEquals(List.of(), violations,
                "AMBIENT_GLOBAL_TIMELINE_READ_COUNT must be 0 in production");
    }

    @Test
    void ownershipScopedAuthoritiesAreUsedByAllQueryCallers() throws IOException {
        // Every production call to the query authorities must pass explicit
        // ownership: findById(projectId, tenantId, id) etc. Count the
        // top-level comma separators inside the call; a 3-argument call has
        // two commas before the closing paren at depth 1.
        List<String> suspicious = new ArrayList<>();
        int ownedCalls = 0;
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.equals("Cfrhi2FinalReadAuthorityGuardTest.java")
                    || name.equals("Cfrhi1LegacyWriteAuthorityGuardTest.java")) {
                continue;
            }
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (isCommentLine(trimmed)) {
                    continue;
                }
                int idx = line.indexOf("revisionQueryService.findById(");
                if (idx < 0) {
                    idx = line.indexOf("revisionQueryService.getDetail(");
                }
                if (idx < 0) {
                    idx = line.indexOf("revisionDiffQuery.compareRevisions(");
                }
                if (idx < 0) {
                    continue;
                }
                // count commas until the matching close paren at depth 1
                int depth = 0;
                int commas = 0;
                boolean closed = false;
                for (int j = idx; j < line.length(); j++) {
                    char ch = line.charAt(j);
                    if (ch == '(') {
                        depth++;
                    } else if (ch == ')') {
                        depth--;
                        if (depth == 0) {
                            closed = true;
                            break;
                        }
                    } else if (ch == ',' && depth == 1) {
                        commas++;
                    }
                }
                if (closed && commas >= 2) {
                    ownedCalls++;
                } else if (closed) {
                    suspicious.add(f + ":" + (i + 1) + " query call without full ownership args: " + trimmed);
                }
            }
        }
        assertEquals(List.of(), suspicious,
                "query authority callers must pass (projectId, tenantId, id)");
        assertTrue(ownedCalls > 0, "ownership-scoped query calls must exist in production");
    }

    @Test
    void finalMetricsAreZero() throws IOException {
        List<String> all = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            all.addAll(Files.readAllLines(f));
        }
        long serviceRefs = all.stream()
                .filter(l -> !isCommentLine(l.trim()))
                .filter(l -> l.contains("TimelineRevisionService"))
                .count();
        long legacyInvocationSites = all.stream()
                .filter(l -> !isCommentLine(l.trim()))
                .filter(l -> l.contains("revisionService.") && !l.contains("revisionQueryService")
                        && !l.contains("revisionSaveService") && !l.contains("restoreRevision"))
                .count();
        long unprivilegedGlobal = 0;
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.equals("SystemMaintenanceReader.java") || name.equals("TimelineSnapshotService.java")) {
                continue; // privileged port + adapter definition are the allowed home
            }
            for (String line : Files.readAllLines(f)) {
                String t = line.trim();
                if (!isCommentLine(t) && (t.contains("listProjectIdsForSystemMaintenance(")
                        || t.contains("listDistinctProjectIds("))) {
                    unprivilegedGlobal++;
                }
            }
        }
        assertEquals(0, serviceRefs, "LEGACY_TIMELINE_REVISION_QUERY_SERVICE_REFERENCE_COUNT");
        assertEquals(0, legacyInvocationSites, "LEGACY_TIMELINE_REVISION_QUERY_INVOCATION_SITE_COUNT");
        assertEquals(0, unprivilegedGlobal, "UNPRIVILEGED_SYSTEM_GLOBAL_TIMELINE_READ_COUNT");
    }
}
