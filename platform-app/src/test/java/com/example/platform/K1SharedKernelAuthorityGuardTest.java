package com.example.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * K1 AR structural guards (K1-AR-01..06). Assert the shared-kernel package layout
 * no longer holds any workflow/automation/provider-registry/effect-execution
 * authority. GCR-2: shared/capability ArtifactRef is RETIRED (SHARED_KERNEL_
 * ARTIFACT_REF_TYPE_COUNT = 0) — the capability family is fully removed from
 * shared-kernel; artifact identity primitives live in shared/identity + shared/digest.
 */
class K1SharedKernelAuthorityGuardTest {

    private static final String CAP = "shared-kernel/src/main/java/com/example/platform/shared/capability";

    private Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle.kts"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("settings.gradle.kts not found");
        }
        return p;
    }

    private boolean exists(String rel) {
        return Files.exists(repoRoot().resolve(rel));
    }

    private List<Path> capRootFiles() {
        Path dir = repoRoot().resolve(CAP);
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** K1-AR-01: no workflow/automation authority (flow, validation, trace packages). */
    @Test
    void k1Ar01_noWorkflowAutomationAuthority() {
        assertFalse(exists(CAP + "/flow"), "K1-AR-01: shared/capability/flow must be absent");
        assertFalse(exists(CAP + "/validation"), "K1-AR-01: shared/capability/validation must be absent");
        assertFalse(exists(CAP + "/trace"), "K1-AR-01: shared/capability/trace must be absent");
    }

    /** K1-AR-02: no provider/runtime registry authority. */
    @Test
    void k1Ar02_noProviderRegistryAuthority() {
        assertFalse(exists(CAP + "/registry"), "K1-AR-02: shared/capability/registry must be absent");
    }

    /** K1-AR-03: no effect execution authority. */
    @Test
    void k1Ar03_noEffectExecutionAuthority() {
        assertFalse(exists(CAP + "/execution"), "K1-AR-03: shared/capability/execution must be absent");
    }

    /** K1-AR-04: GCR-2 — shared/capability ArtifactRef RETIRED; capability root is empty/absent. */
    @Test
    void k1Ar04_capabilityArtifactRefRetired() {
        Path dir = repoRoot().resolve(CAP);
        if (Files.exists(dir)) {
            List<Path> files = capRootFiles();
            assertTrue(files.isEmpty(),
                    "K1-AR-04: shared/capability root must be empty after ArtifactRef retirement, found: " + files);
        }
        assertFalse(exists(CAP + "/ArtifactRef.java"), "K1-AR-04: shared ArtifactRef must be deleted (GCR-2)");
    }

    /** K1-AR-05: GCR-2 — no capability-family type survives; artifact primitives live in identity/digest. */
    @Test
    void k1Ar05_capabilityFamilyRetired() {
        assertFalse(exists(CAP + "/ArtifactRef.java"), "K1-AR-05: ArtifactRef must NOT be retained (GCR-2)");
        // Artifact identity + integrity primitives now live in shared/identity and shared/digest.
        assertTrue(exists("shared-kernel/src/main/java/com/example/platform/shared/identity/ArtifactId.java"),
                "K1-AR-05: ArtifactId primitive must exist in shared/identity");
        assertTrue(exists("shared-kernel/src/main/java/com/example/platform/shared/digest/ContentDigest.java"),
                "K1-AR-05: ContentDigest primitive must exist in shared/digest (GCR-2)");
        // No other capability-family packages may exist.
        String[] retired = {"action", "event", "execution", "flow", "hook", "registry", "trace", "validation"};
        for (String sub : retired) {
            assertFalse(exists(CAP + "/" + sub), "K1-AR-05: retired package must remain absent: " + sub);
        }
    }

    /** K1-AR-06: GCR-2 — no production usage of shared ArtifactRef anywhere. */
    @Test
    void k1Ar06_noSharedArtifactRefProductionUsage() {
        assertFalse(exists(CAP + "/ArtifactRef.java"),
                "K1-AR-06: shared ArtifactRef type absent ⇒ production usage count = 0");
    }
}
