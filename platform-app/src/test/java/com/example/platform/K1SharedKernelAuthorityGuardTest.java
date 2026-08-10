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
 * K1 AR structural guards (K1-AR-01..05). Assert the shared-kernel package layout
 * no longer holds any workflow/automation/provider-registry/effect-execution
 * authority, and that ArtifactRef is the sole surviving capability-family type.
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

    /** K1-AR-04: retired legacy capability skeleton absent — root contains ONLY ArtifactRef.java. */
    @Test
    void k1Ar04_capabilityRootOnlyArtifactRef() {
        List<Path> files = capRootFiles();
        assertTrue(files.size() == 1 && files.get(0).getFileName().toString().equals("ArtifactRef.java"),
                "K1-AR-04: shared/capability root must contain ONLY ArtifactRef.java, found: " + files);
    }

    /** K1-AR-05: ArtifactRef remains the only capability-family type (canonical primitive preserved). */
    @Test
    void k1Ar05_artifactRefSoleCapabilityType() {
        assertTrue(exists(CAP + "/ArtifactRef.java"), "K1-AR-05: ArtifactRef must be retained");
        // No other capability-family packages may exist.
        String[] retired = {"action", "event", "execution", "flow", "hook", "registry", "trace", "validation"};
        for (String sub : retired) {
            assertFalse(exists(CAP + "/" + sub), "K1-AR-05: retired package must remain absent: " + sub);
        }
    }
}
