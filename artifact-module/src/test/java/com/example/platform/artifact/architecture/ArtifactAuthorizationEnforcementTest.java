package com.example.platform.artifact.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ARTIFACT_AUTHORITY_CONTRACT_V1 enforcement guard (mirrors
 * {@code AuthorizationArchitectureGuardTest}'s source-boundary style).
 *
 * <p>Every Artifact REST controller must consult the canonical project-scoped
 * authorization boundary ({@link com.example.platform.artifact.app.ArtifactProjectAuthorizationPort})
 * before it reads or mutates data. The boundary's composition-root implementation
 * must reach the single canonical Identity {@code AuthorizationDecisionPort}; it may
 * not perform an entitlement/flag decision or bypass {@code requireAuthorized}.</p>
 *
 * <p>Test working directory is the module root ({@code artifact-module}).</p>
 */
class ArtifactAuthorizationEnforcementTest {

    private static final Path ARTIFACT_API =
            Path.of("src/main/java/com/example/platform/artifact/api");
    private static final Path ARTIFACT_APP =
            Path.of("src/main/java/com/example/platform/artifact/app");
    private static final Path PLATFORM_APP_ARTIFACT =
            Path.of("../platform-app/src/main/java/com/example/platform/web/artifact");

    private static final String PORT_TYPE = "ArtifactProjectAuthorizationPort";
    private static final String AUTHORIZATION_CALL_MARKER = "projectAuthorization.require";

    /** The complete Artifact REST controller set — the protected surface. */
    private static final List<String> PROTECTED_CONTROLLERS = List.of(
            "ArtifactApplicationController.java",
            "ArtifactController.java",
            "ArtifactLifecycleController.java");

    // ── helpers ─────────────────────────────────────────────────────────────

    private static List<Path> controllers() {
        if (!Files.isDirectory(ARTIFACT_API)) {
            fail("artifact api package not found: " + ARTIFACT_API.toAbsolutePath());
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(ARTIFACT_API)) {
            return stream.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            fail("scan failed for " + ARTIFACT_API + ": " + e.getMessage());
            return List.of();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            fail("read failed for " + file + ": " + e.getMessage());
            return "";
        }
    }

    /** The single source-boundary rule, factored out so it can be falsified. */
    private static List<String> violations(String controllerSource, String controllerName) {
        List<String> found = new ArrayList<>();
        if (!controllerSource.contains(PORT_TYPE)) {
            found.add(controllerName + " must depend on " + PORT_TYPE);
        }
        if (!controllerSource.contains(AUTHORIZATION_CALL_MARKER)) {
            found.add(controllerName + " must call " + AUTHORIZATION_CALL_MARKER + " before data access");
        }
        return found;
    }

    /** Simulates an unauthorized controller by removing every authorization reference. */
    private static String withoutAuthorization(String source) {
        return source.lines()
                .filter(line -> !line.contains("projectAuthorization") && !line.contains(PORT_TYPE))
                .collect(Collectors.joining("\n"));
    }

    // ── enforcement ─────────────────────────────────────────────────────────

    @Test
    void everyArtifactRestControllerConsultsTheProjectAuthorizationBoundary() {
        List<String> names = controllers().stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();
        assertEquals(PROTECTED_CONTROLLERS, names,
                "Artifact controller set changed; add the new surface to this enforcement guard");

        List<String> allViolations = new ArrayList<>();
        for (Path controller : controllers()) {
            allViolations.addAll(violations(read(controller), controller.getFileName().toString()));
        }
        assertTrue(allViolations.isEmpty(),
                "Artifact controllers must be project-scoped authorized: " + allViolations);
    }

    @Test
    void lifecycleControllerSeparatesProjectReadAndWriteAuthorization() {
        String source = read(ARTIFACT_API.resolve("ArtifactLifecycleController.java"));
        assertTrue(source.contains("projectAuthorization.requireRead"),
                "delete-check/overview reads must authorize project READ");
        assertTrue(source.contains("projectAuthorization.requireWrite"),
                "tombstone/gc mutations must authorize project WRITE");
    }

    @Test
    void projectAuthorizationPortStaysOnTheCanonicalDecisionPath() {
        Path port = ARTIFACT_APP.resolve(PORT_TYPE + ".java");
        assertTrue(Files.isRegularFile(port),
                "the owner-published authorization port must exist: " + port);
        assertFalse(read(port).contains("EntitlementService"),
                "authorization must not consult entitlement (AR-AUTH-003/004/005)");

        Path adapter = PLATFORM_APP_ARTIFACT.resolve("WebArtifactProjectAuthorization.java");
        assertTrue(Files.isRegularFile(adapter),
                "the composition-root authorization adapter must exist: " + adapter);
        String adapterSource = read(adapter);
        assertTrue(adapterSource.contains("AuthorizationDecisionPort"),
                "the adapter must depend on the canonical decision port");
        assertTrue(adapterSource.contains("requireAuthorized"),
                "the adapter must fail closed through requireAuthorized");
        assertFalse(adapterSource.contains(".decide("),
                "the adapter must not bypass requireAuthorized with a raw decide() call");
        assertFalse(adapterSource.toLowerCase().contains("entitlement"),
                "authorization must not consult entitlement (AR-AUTH-003/004/005)");
        assertTrue(adapterSource.contains("AuthorizationResourceType.PROJECT"),
                "the artifact action must be project-scoped");
    }

    // ── falsification ───────────────────────────────────────────────────────

    @Test
    void removingAuthorizationFromAnyControllerIsDetected() {
        for (String name : PROTECTED_CONTROLLERS) {
            String authorized = read(ARTIFACT_API.resolve(name));
            assertTrue(violations(authorized, name).isEmpty(),
                    "current controller must already satisfy the guard: " + name);

            String stripped = withoutAuthorization(authorized);
            assertNotEquals(authorized, stripped,
                    "falsification fixture must actually remove authorization from " + name);
            assertFalse(violations(stripped, name).isEmpty(),
                    "the guard MUST fail when authorization is removed from " + name);
        }
    }
}
