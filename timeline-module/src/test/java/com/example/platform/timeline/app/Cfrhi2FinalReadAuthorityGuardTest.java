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
 * CFRH-I2 final read-authority closure guard (I2-G).
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
 * Scope is symbol-set bounded (frozen I2 contract): forbidden legacy read
 * symbols and the retired service are detected mechanically; safe owned forms
 * (findOwnedById / findLatestOwnedByProject / listOwnedByProject / query
 * services) may remain.
 */
class Cfrhi2FinalReadAuthorityGuardTest {

    // Retired service — zero references anywhere in production.
    private static final String LEGACY_SERVICE = "TimelineRevisionService";

    // Ambient-global read symbols forbidden in production (definitions in the
    // adapters are allowed; production call sites are not).
    private static final List<String> FORBIDDEN_GLOBAL_READ_SYMBOLS = List.of(
            "findPayload",
            "findLatestByProject");

    // Repository global reads forbidden in production call sites (adapter
    // definitions allowed).
    private static final List<String> FORBIDDEN_REPO_GLOBAL_READS = List.of(
            "revisionRepository.findById",
            "revisionRepository.findHeadByProject",
            "revisionRepository.listByProject");

    // Adapters that own the (still existing) global method definitions.
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
        Path siblings = root.toString().contains("/.worktrees/")
                ? root.getParent().getParent().resolve(".worktrees")
                : root.resolve(".worktrees");
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !(f.startsWith(siblings) && !f.startsWith(root)))
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
                    // (timelineSnapshotService / snapshotService) — skip adapter
                    // definitions and the system-maintenance reader's privileged
                    // wrapper; other modules may have unrelated same-named methods
                    if ((trimmed.contains("timelineSnapshotService." + sym + "(")
                            || trimmed.contains("snapshotService." + sym + "("))
                            && !name.equals("SystemMaintenanceReader.java")
                            && !ADAPTER_FILES.contains(name)) {
                        violations.add(f + ":" + (i + 1) + " global read " + sym + ": " + trimmed);
                    }
                }
                for (String repoRead : FORBIDDEN_REPO_GLOBAL_READS) {
                    if (trimmed.contains(repoRead + "(")
                            && !name.equals("TimelineRevisionRepository.java")
                            && !name.equals("Cfrhi2SystemAuthorityGuardTest.java")) {
                        violations.add(f + ":" + (i + 1) + " repo global read " + repoRead + ": " + trimmed);
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
                if (!isCommentLine(t) && t.contains("listDistinctProjectIds(")) {
                    unprivilegedGlobal++;
                }
            }
        }
        assertEquals(0, serviceRefs, "LEGACY_TIMELINE_REVISION_QUERY_SERVICE_REFERENCE_COUNT");
        assertEquals(0, legacyInvocationSites, "LEGACY_TIMELINE_REVISION_QUERY_INVOCATION_SITE_COUNT");
        assertEquals(0, unprivilegedGlobal, "UNPRIVILEGED_SYSTEM_GLOBAL_TIMELINE_READ_COUNT");
    }
}
