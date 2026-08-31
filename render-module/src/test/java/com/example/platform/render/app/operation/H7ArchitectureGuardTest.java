package com.example.platform.render.app.operation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executes the single invariant-oriented H7 production-source guard. */
class H7ArchitectureGuardTest {

    private static final Path ROOT = repositoryRoot(Path.of(System.getProperty("user.dir")));
    private static final Path GUARD = ROOT.resolve("scripts/guards/h7-architecture-guard.py");
    private static final List<String> REQUIRED_ZERO_LAWS = List.of(
            "PRODUCT_CURRENT_REVISION_CORRECTNESS_AUTHORITY_COUNT",
            "PRODUCT_LOCAL_MAX_PLUS_ONE_REVISION_ALLOCATION_COUNT",
            "NON_CANONICAL_TIMELINE_HEAD_WRITER_COUNT",
            "DUPLICATE_PROJECT_REVISION_ALLOCATOR_AUTHORITY_COUNT",
            "NORMAL_EDIT_MISSING_PARENT_EDGE_PATH_COUNT",
            "DIRECT_TIMELINE_REF_MUTATION_OUTSIDE_SHARED_AUTHORITY_COUNT",
            "H7_DIRECT_DB_WRITE_OUTSIDE_APPLICATION_TRANSACTION_BOUNDARY_COUNT",
            "AUTHORIZATION_PLAN_BINDING_MISSING_COUNT",
            "DURABLE_IDEMPOTENCY_TRANSACTION_MISSING_COUNT",
            "SHARED_TIMELINE_REF_DELEGATION_MISSING_COUNT",
            "SHARED_PROJECT_REVISION_ALLOCATOR_DELEGATION_MISSING_COUNT",
            "H7_NO_OP_EXPECTED_REF_VALIDATION_MISSING_COUNT",
            "GENESIS_ZERO_PARENT_BOOTSTRAP_SEMANTICS_MISSING_COUNT",
            "UNCLASSIFIED");

    @Test
    void canonicalAuthoritiesAndH7TransactionLawsPass() throws Exception {
        GuardResult result = runGuard(ROOT, false);

        assertEquals(0, result.exitCode(), result.output());
        for (String law : REQUIRED_ZERO_LAWS) {
            assertTrue(result.output().contains(law + "=0"),
                    () -> "missing exact zero law " + law + "\n" + result.output());
        }
        assertTrue(result.output().contains(
                "CANONICAL_TIMELINE_REF_MUTATION_AUTHORITY_TYPE_COUNT=1"), result.output());
        assertTrue(result.output().contains(
                "CANONICAL_PROJECT_REVISION_ALLOCATOR_TYPE_COUNT=1"), result.output());
        assertTrue(result.output().contains("H7_ARCHITECTURE_GUARD=PASS"), result.output());
    }

    @Test
    void deterministicMutationMatrixRejectsEveryNegativeControl() throws Exception {
        GuardResult result = runGuard(ROOT, true);

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("MUTATION_MATRIX_TOTAL=13"), result.output());
        assertTrue(result.output().contains("MUTATION_MATRIX_FAILURES=0"), result.output());
        assertEquals(13, count(result.output(), "MUTATION "), result.output());
        assertEquals(13, count(result.output(), "=PASS "), result.output());
        assertFalse(result.output().contains("=FAIL "), result.output());
    }

    @Test
    void missingRepositoryRootFailsClosed(@TempDir Path outsideRepository) throws Exception {
        GuardResult result = runGuard(outsideRepository, false);

        assertTrue(result.exitCode() != 0, result.output());
        assertTrue(result.output().contains("UNCLASSIFIED=1"), result.output());
    }

    private static GuardResult runGuard(Path root, boolean selfTest)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                selfTest
                        ? List.of("python3", GUARD.toString(), "--root", root.toString(), "--self-test")
                        : List.of("python3", GUARD.toString(), "--root", root.toString()));
        Process process = builder.redirectErrorStream(true).start();
        boolean completed = process.waitFor(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new AssertionError("H7 architecture guard exceeded 60 seconds");
        }
        return new GuardResult(process.exitValue(), new String(process.getInputStream().readAllBytes()));
    }

    private static Path repositoryRoot(Path start) {
        for (Path current = start.toAbsolutePath().normalize(); current != null;
             current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
                return current;
            }
        }
        throw new IllegalStateException("repository root not found from " + start);
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
            count++;
        }
        return count;
    }

    private record GuardResult(int exitCode, String output) {
    }
}
