package com.example.platform.render.app.planner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PRE-#21 W3 — module boundary guard (C6).
 *
 * CRITICAL_CROSS_MODULE_INTERNAL_ACCESS_COUNT = 0 for the retired
 * timeline.internal package (renamed to timeline.diff.merge in W3). Any
 * reference to the old internal package name fails the guard.
 *
 * Also asserts the static Modulith snapshot has been retired (no stale
 * generated snapshot posing as verification authority).
 */
class Pre21ModuleBoundaryGuardTest {

    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir"));
        while (p != null && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return p;
    }

    private static List<Path> allJavaFiles() throws IOException {
        Path root = repoRoot();
        boolean rootIsWorktree = root.toString().contains("/.worktrees/");
        Path worktreesDir = rootIsWorktree
                ? root.getParent().getParent().resolve(".worktrees")
                : root.resolve(".worktrees");
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.startsWith(worktreesDir) || (rootIsWorktree && f.startsWith(root)))
                    .toList();
        }
    }

    @Test
    void noReferenceToRetiredTimelineInternalPackage() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : allJavaFiles()) {
            if (f.getFileName().toString().equals("Pre21ModuleBoundaryGuardTest.java")) {
                continue; // guard test itself references the retired name in assertions
            }
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.startsWith("//") || t.startsWith("*")) {
                    continue;
                }
                if (t.contains("com.example.platform.timeline.internal")
                        || t.contains("timeline.internal.")) {
                    violations.add(f.getFileName() + ":" + (i + 1) + ": " + t);
                }
            }
        }
        assertEquals(List.of(), violations,
                "CRITICAL_CROSS_MODULE_INTERNAL_ACCESS_COUNT must be 0 — timeline.internal is retired");
    }

    @Test
    void staticModulithSnapshotIsNotCommitted() throws IOException {
        // ModulithDocumentationGenerationTest regenerates the architecture map
        // at test time; the committed static snapshot (91 files) was retired.
        // Guard: no generated/modulith file may be git-tracked.
        Path gen = repoRoot().resolve("platform-app/docs/architecture/maps/generated/modulith");
        if (!Files.exists(gen)) {
            return; // not generated in this run — fine
        }
        Process p;
        try {
            p = new ProcessBuilder("git", "-C", repoRoot().toString(), "ls-files",
                    "platform-app/docs/architecture/maps/generated/modulith")
                    .redirectErrorStream(true).start();
        } catch (IOException e) {
            return; // not a git checkout — skip
        }
        String out;
        try (var is = p.getInputStream()) {
            out = new String(is.readAllBytes());
        }
        assertTrue(out.isBlank(),
                "static Modulith snapshot must not be committed (retired in W3): " + out);
    }

    @Test
    void mergeSemanticSurfaceIsInDiffMergePackage() throws IOException {
        Path mergePkg = repoRoot().resolve("timeline-module/src/main/java/com/example/platform/timeline/diff/merge");
        assertTrue(Files.isDirectory(mergePkg), "timeline.diff.merge package must exist");
        try (Stream<Path> walk = Files.walk(mergePkg)) {
            long n = walk.filter(Files::isRegularFile).count();
            assertTrue(n >= 16, "expected the 16 semantic merge types, found " + n);
        }
    }
}
