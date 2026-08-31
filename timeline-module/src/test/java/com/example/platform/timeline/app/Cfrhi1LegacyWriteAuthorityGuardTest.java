package com.example.platform.timeline.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CFRH-I1 postcondition guard.
 *
 * Proves the legacy timeline revision semantic-write authority is fully closed:
 *
 *   LEGACY_TIMELINE_REVISION_SEMANTIC_WRITE_AUTHORITY_COUNT = 0
 *
 * The guard scans production Java sources for the frozen forbidden symbols
 * (definitions AND references):
 *
 *   - TimelineRevisionService.recordRevision
 *   - TimelineRevisionService.recordAiAdoptRevision
 *   - TimelineRevisionService.backfillHeadFromLatestSnapshot
 *   - TimelineRevisionService.restore   (legacy service authority; NOT
 *     TimelineRevisionSaveService.restoreRevision — the canonical authority)
 *
 * The canonical restore path (TimelineRevisionSaveService.restoreRevision) is
 * the single allowed restore authority and is asserted present.
 *
 * KNOWN_FORBIDDEN_LEGACY_TIMELINE_WRITE_SYMBOL_COUNT is symbol-set bounded:
 * it detects the frozen forbidden symbols; it does not claim to detect
 * arbitrarily renamed writers.
 */
class Cfrhi1LegacyWriteAuthorityGuardTest {

    private static final List<String> FORBIDDEN_PRODUCTION_SYMBOLS = List.of(
            "recordRevision",
            "recordAiAdoptRevision",
            "backfillHeadFromLatestSnapshot",
            "restore");
    // restore is forbidden ONLY as a TimelineRevisionService method; the
    // canonical TimelineRevisionSaveService.restoreRevision is the allowed
    // authority. The legacy service class itself is matched separately.

    private static final String LEGACY_SERVICE = "TimelineRevisionService.java";
    private static final String CANONICAL_SERVICE = "TimelineRevisionSaveService.java";

    private static Path repoRoot() {
        // Gradle test worker user.dir = module dir; walk up to the repository root
        // (settings.gradle marker). Fall back to an upward walk from user.dir.
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cur = start;
        while (cur != null && !Files.exists(cur.resolve("settings.gradle.kts"))
                && !Files.exists(cur.resolve("settings.gradle"))) {
            cur = cur.getParent();
        }
        if (cur != null) {
            return cur;
        }
        return start;
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
                    // exclude sibling worktrees — only the checked-out tree counts.
                    // When running inside a worktree, root itself lives under
                    // /.worktrees/ so the exclusion must keep root-prefixed files.
                    .filter(f -> !f.startsWith(worktreesDir) || (rootIsWorktree && f.startsWith(root)))
                    .forEach(out::add);
        }
        return out;
    }

    @Test
    void legacySemanticWriteAuthorityIsZero() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                // ignore comments (line-level: leading // or block comment lines)
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue;
                }
                for (String symbol : FORBIDDEN_PRODUCTION_SYMBOLS) {
                    if (symbol.equals("restore")) {
                        // 'restore' is forbidden ONLY as a legacy TimelineRevisionService
                        // method invocation: `revisionService.restore(` or `this.restore(`
                        // or a lowercase-instance `.restore(` — must NOT match static
                        // calls like RevisionCommandPlanDigest.restore(...), nor the
                        // canonical `restoreRevision` / `restoreVerifier` identifiers.
                        if (!line.contains(".restore(") || line.contains("restoreRevision")
                                || line.contains("restoreVerifier")) {
                            continue;
                        }
                        // exclude static calls: UppercaseType.restore(
                        if (line.matches(".*\\b[A-Z][\\w.]*\\.restore\\(.*")) {
                            continue;
                        }
                        if (name.equals(CANONICAL_SERVICE)) {
                            continue; // canonical service is the allowed authority
                        }
                        violations.add(f + ":" + (i + 1) + " legacy TimelineRevisionService.restore call: " + trimmed);
                    } else {
                        // recordRevision / recordAiAdoptRevision / backfillHeadFromLatestSnapshot
                        // forbidden as definitions or references anywhere in production.
                        // Only flag actual identifiers (word boundary), and ignore
                        // occurrences inside the guard's own legal javadoc/comment lines.
                        if (line.matches(".*\\b" + java.util.regex.Pattern.quote(symbol) + "\\b.*")) {
                            violations.add(f + ":" + (i + 1) + " forbidden symbol '" + symbol + "': " + trimmed);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), violations,
                "LEGACY_TIMELINE_REVISION_SEMANTIC_WRITE_AUTHORITY_COUNT must be 0");
    }

    @Test
    void canonicalRestoreAuthorityIsActive() throws IOException {
        // H7 V2 has one ownership-explicit restore implementation plus one
        // trusted owner-resolving adapter. Count the structural signatures and
        // delegation instead of lexical restoreRevision occurrences: the token
        // also appears at the adapter call site and therefore is not an
        // authority count.
        Path root = repoRoot();
        long canonicalDefs = countPattern(root, "TimelineRevisionSaveService.java",
                "\\bpublic\\s+TimelineRevision\\s+restoreRevision\\s*\\(\\s*"
                        + "String\\s+tenantId\\s*,\\s*String\\s+productId\\s*,\\s*"
                        + "String\\s+historicalRevisionId\\s*,\\s*"
                        + "String\\s+expectedCurrentRevisionId\\s*,\\s*"
                        + "String\\s+canonicalAuthor\\s*\\)\\s*\\{");
        assertEquals(1, canonicalDefs,
                "CANONICAL_RESTORE_AUTHORITY_COUNT must be 1 (root=" + root + ")");
        long ownerResolvingAdapters = countPattern(root, "TimelineRevisionSaveService.java",
                "\\bpublic\\s+TimelineRevision\\s+restoreRevision\\s*\\(\\s*"
                        + "String\\s+productId\\s*,\\s*String\\s+historicalRevisionId\\s*,\\s*"
                        + "String\\s+expectedCurrentRevisionId\\s*,\\s*"
                        + "String\\s+createdBy\\s*\\)\\s*\\{");
        assertEquals(1, ownerResolvingAdapters,
                "canonical restore must expose exactly one trusted owner-resolving adapter");
        long adapterDelegations = countPattern(root, "TimelineRevisionSaveService.java",
                "\\breturn\\s+restoreRevision\\s*\\(\\s*"
                        + "resolveProjectTenant\\s*\\(\\s*productId\\s*\\)\\s*,\\s*"
                        + "productId\\s*,\\s*historicalRevisionId\\s*,\\s*"
                        + "expectedCurrentRevisionId\\s*,\\s*createdBy\\s*\\)\\s*;");
        assertEquals(1, adapterDelegations,
                "trusted owner-resolving restore adapter must delegate exactly once");
        long controllerRefs = countPattern(root, "TimelineRevisionController.java",
                "\\brevisionSaveService\\s*\\.\\s*restoreRevision\\s*\\(");
        assertEquals(1, controllerRefs,
                "TimelineRevisionController must call the canonical restoreRevision exactly once (root=" + root + ")");
    }

    @Test
    void legacyServiceDeletedAfterReplacementClosure() throws IOException {
        // CFRH-I2-E: TimelineRevisionService class must be DELETED after full
        // behavioral replacement closure (all 22 production invocation sites migrated).
        Path root = repoRoot();
        long legacyDefs = countSymbol(root, "TimelineRevisionService.java", "class TimelineRevisionService");
        assertEquals(0, legacyDefs,
                "LEGACY_TIMELINE_REVISION_QUERY_SERVICE_CLASS_COUNT must be 0 (deleted in I2-E)");
        // replacement ownership-scoped authorities must exist
        long queryDefs = countSymbol(root, "TimelineRevisionQueryService.java", "class TimelineRevisionQueryService");
        assertEquals(1, queryDefs, "TimelineRevisionQueryService must exist (I2-A)");
        long diffDefs = countSymbol(root, "TimelineRevisionDiffQuery.java", "class TimelineRevisionDiffQuery");
        assertEquals(1, diffDefs, "TimelineRevisionDiffQuery must exist (I2-A)");
        // and neither replacement exposes legacy semantic write methods
        for (String symbol : FORBIDDEN_PRODUCTION_SYMBOLS) {
            long q = countSymbol(root, "TimelineRevisionQueryService.java", symbol);
            long d = countSymbol(root, "TimelineRevisionDiffQuery.java", symbol);
            assertEquals(0, q + d, "query authorities must not define legacy write symbol '" + symbol + "'");
        }
    }

    private static long countSymbol(Path root, String fileName, String token) throws IOException {
        boolean rootIsWorktree = root.toString().contains("/.worktrees/");
        Path worktreesDir = rootIsWorktree
                ? root.getParent().getParent().resolve(".worktrees")
                : root.resolve(".worktrees");
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().equals(fileName))
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> !f.startsWith(worktreesDir) || (rootIsWorktree && f.startsWith(root)))
                    .flatMap(f -> {
                        try {
                            return Files.readAllLines(f).stream();
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    // exclude comment lines
                    .filter(l -> {
                        String t = l.trim();
                        return !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*");
                    })
                    .filter(l -> l.contains(token))
                    .count();
        }
    }

    private static long countPattern(Path root, String fileName, String regex) throws IOException {
        boolean rootIsWorktree = root.toString().contains("/.worktrees/");
        Path worktreesDir = rootIsWorktree
                ? root.getParent().getParent().resolve(".worktrees")
                : root.resolve(".worktrees");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                regex, java.util.regex.Pattern.MULTILINE | java.util.regex.Pattern.DOTALL);
        long count = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().equals(fileName))
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> !f.startsWith(worktreesDir) || (rootIsWorktree && f.startsWith(root)))
                    .toList()) {
                String source = Files.readString(file)
                        .replaceAll("(?s)/\\*.*?\\*/", " ")
                        .replaceAll("(?m)//[^\\n]*", " ");
                count += pattern.matcher(source).results().count();
            }
        }
        return count;
    }
}
