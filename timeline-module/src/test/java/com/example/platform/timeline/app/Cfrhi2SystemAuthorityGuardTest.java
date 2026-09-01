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
 * CFRH-I2 system-authority structural guard.
 *
 * Proves the explicit privileged read port (SystemMaintenanceReader) is the
 * ONLY production home for system-wide timeline enumeration:
 *
 *   - TimelineSnapshotService.listProjectIdsForSystemMaintenance may be invoked ONLY from
 *     SystemMaintenanceReader.java (the explicit privileged port).
 *   - No other production class may perform its own global TIMELINE_SNAPSHOT
 *     enumeration via direct jOOQ (pattern: selectDistinct + TIMELINE_SNAPSHOT
 *     .PROJECT_ID outside the snapshot service).
 *   - The approved system-maintenance consumers (GlobalAssetIntegrityService
 *     and TimelineAssetGcService) must
 *     reference SystemMaintenanceReader.
 *
 * Guard scope is symbol-set bounded (frozen I2 contract): it detects the
 * known forbidden patterns, not arbitrarily renamed global readers.
 */
class Cfrhi2SystemAuthorityGuardTest {

    private static final String SYSTEM_READER = "SystemMaintenanceReader.java";
    private static final String SNAPSHOT_SERVICE = "TimelineSnapshotService.java";

    private static final List<String> APPROVED_CONSUMERS = List.of(
            "GlobalAssetIntegrityService.java",
            "TimelineAssetGcService.java");

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

    @Test
    void globalEnumerationIsRestrictedToSystemMaintenanceReader() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.equals(SYSTEM_READER)) {
                continue; // the privileged port itself
            }
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue;
                }
                // direct call to the global enumeration on the snapshot service
                // (exclude the adapter's own method definition)
                if (trimmed.contains("listProjectIdsForSystemMaintenance(")
                        && !name.equals(SNAPSHOT_SERVICE)) {
                    violations.add(f + ":" + (i + 1) + " direct listProjectIdsForSystemMaintenance outside SystemMaintenanceReader");
                }
                // direct jOOQ global scan of TIMELINE_SNAPSHOT project ids outside the adapter
                if (trimmed.contains("selectDistinct")
                        && trimmed.contains("TIMELINE_SNAPSHOT")
                        && !name.equals(SNAPSHOT_SERVICE)) {
                    violations.add(f + ":" + (i + 1) + " direct jOOQ global TIMELINE_SNAPSHOT scan: " + trimmed);
                }
            }
        }
        assertEquals(List.of(), violations,
                "SYSTEM_GLOBAL_READ_WITHOUT_PRIVILEGED_PORT_COUNT must be 0");
    }

    @Test
    void approvedConsumersUseTheSystemMaintenanceReaderPort() throws IOException {
        for (String consumer : APPROVED_CONSUMERS) {
            Path f = findFile(consumer);
            assertTrue(f != null, "approved consumer missing: " + consumer);
            String content = String.join("\n", Files.readAllLines(f));
            assertTrue(content.contains("SystemMaintenanceReader"),
                    consumer + " must reference SystemMaintenanceReader");
            // no consumer may hold a direct system-primitive call anymore
            assertTrue(!content.contains("listProjectIdsForSystemMaintenance(")
                            || consumer.equals(SYSTEM_READER),
                    consumer + " must not call listProjectIdsForSystemMaintenance directly");
        }
    }

    @Test
    void noUnexpectedSystemMaintenanceReaderConsumers() throws IOException {
        // F. mechanically detect ANY production consumer of SystemMaintenanceReader;
        // the approved set must be exactly the frozen consumers.
        List<String> consumers = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.equals(SYSTEM_READER)) {
                continue;
            }
            if (name.equals("Cfrhi2SystemAuthorityGuardTest.java")
                    || name.equals("Cfrhi2FinalReadAuthorityGuardTest.java")) {
                continue;
            }
            String content = String.join("\n", Files.readAllLines(f));
            if (content.contains("SystemMaintenanceReader")) {
                consumers.add(name);
            }
        }
        assertEquals(APPROVED_CONSUMERS.stream().sorted().toList(),
                consumers.stream().sorted().toList(),
                "UNAUTHORIZED_SYSTEM_READER_CONSUMER_COUNT must be 0 (approved set = exactly 2)");
    }

    @Test
    void systemReaderExistsAndExposesThePrivilegedSurface() throws IOException {
        Path f = findFile(SYSTEM_READER);
        assertTrue(f != null, "SystemMaintenanceReader must exist (I2-F)");
        String content = String.join("\n", Files.readAllLines(f));
        assertTrue(content.contains("listProjectIdsWithSnapshots"),
                "SystemMaintenanceReader must expose listProjectIdsWithSnapshots()");
        assertTrue(content.contains("findLatestSnapshot"),
                "SystemMaintenanceReader must expose findLatestSnapshot(projectId)");
    }

    private static Path findFile(String fileName) throws IOException {
        for (Path f : productionJavaFiles()) {
            if (f.getFileName().toString().equals(fileName)) {
                return f;
            }
        }
        return null;
    }
}
