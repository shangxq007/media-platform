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
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> f.toString().endsWith(".java"))
                    // exclude sibling worktrees — only the checked-out tree counts
                    .filter(f -> !f.toString().contains("/.worktrees/"))
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
        // the canonical restore authority must exist and be referenced by the controller
        Path root = repoRoot();
        long canonicalDefs = countSymbol(root, "TimelineRevisionSaveService.java", "restoreRevision");
        assertEquals(1, canonicalDefs,
                "CANONICAL_RESTORE_AUTHORITY_COUNT must be 1 (root=" + root + ")");
        long controllerRefs = countSymbol(root, "TimelineRevisionController.java", "restoreRevision");
        assertEquals(1, controllerRefs,
                "TimelineRevisionController must call the canonical restoreRevision exactly once (root=" + root + ")");
    }

    @Test
    void legacyServiceRetainedForI2Queries() throws IOException {
        // TimelineRevisionService class must still exist (I2 query closure owner)
        Path root = repoRoot();
        long legacyDefs = countSymbol(root, "TimelineRevisionService.java", "class TimelineRevisionService");
        assertEquals(1, legacyDefs, "TimelineRevisionService must remain for I2 query closure");
        // but it must expose NO semantic write methods
        for (String symbol : FORBIDDEN_PRODUCTION_SYMBOLS) {
            long defs = countSymbol(root, "TimelineRevisionService.java",
                    symbol.equals("restore") ? "RestoreResult" : symbol);
            assertEquals(0, defs, "TimelineRevisionService must not define '" + symbol + "'");
        }
    }

    private static long countSymbol(Path root, String fileName, String token) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().equals(fileName))
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> !f.toString().contains("/.worktrees/"))
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
}
