package com.example.platform.execution.composition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Phase5CompositionArchitectureTest {

    private static final Pattern FORBIDDEN_RUNTIME_AUTHORITY = Pattern.compile(
            "\\b(?:PhysicalHostAvailability|WorkerRuntimeAvailability|HostResourceSnapshot|"
                    + "SchedulableCapacity|Reservation|ObservedUsage|ProviderProbeResult|TaskLease|"
                    + "ExecutionAssignment|ExecutionAttempt|ExecutionBackend|heartbeat|utilization|"
                    + "currentTimeMillis|Instant\\.now|randomUUID|Math\\.random)\\b");

    @Test
    void phase5HasExactlyTheFrozenTenBlockerClasses() {
        assertEquals(Set.of(
                        CompositionBlocker.MANDATORY_INTERMEDIATE_ARTIFACT,
                        CompositionBlocker.PROVIDER_NATIVE_PIPELINE_UNSUPPORTED,
                        CompositionBlocker.DEVICE_CONTEXT_INCOMPATIBLE,
                        CompositionBlocker.SOFTWARE_HARDWARE_FRAME_BOUNDARY,
                        CompositionBlocker.SANDBOX_OR_TRUST_BOUNDARY,
                        CompositionBlocker.BRANCHING_SEMANTIC_BOUNDARY,
                        CompositionBlocker.RETRY_OR_FAILURE_ISOLATION_BOUNDARY,
                        CompositionBlocker.DETERMINISM_INCOMPATIBLE,
                        CompositionBlocker.INPUT_OUTPUT_CONTRACT_INCOMPATIBLE,
                        CompositionBlocker.UNKNOWN_PROVIDER_COMPOSITION_SEMANTICS),
                Set.of(CompositionBlocker.values()));
    }

    @Test
    void phase5ImportsNoMutableRuntimeOrWorkerFabricAuthority() throws IOException {
        String source = readJavaSources(compositionSourceRoot());
        assertEquals(0, FORBIDDEN_RUNTIME_AUTHORITY.matcher(stripComments(source)).results().count(),
                "PHASE5_MUTABLE_RUNTIME_AUTHORITY_COUNT=0");
        assertFalse(source.contains("workerfabric"));
        assertFalse(source.contains("worker-fabric"));
        assertFalse(Files.readString(repoRoot().resolve("media-execution-plan-module/build.gradle.kts"))
                .contains("worker-fabric-module"));
    }

    @Test
    void phase5DefinesNoPhase6TypeOrPhysicalPlanRewriteSurface() throws IOException {
        String source = stripComments(readJavaSources(compositionSourceRoot()));
        Pattern phase6Definition = Pattern.compile(
                "\\b(?:class|record|interface|enum)\\s+(?:ExecutableTask|ExecutableTaskId|"
                        + "ExecutableTaskGraphDigest|BoundaryAction|ProviderBoundExecutableTaskGraph)\\b");
        assertEquals(0, phase6Definition.matcher(source).results().count(),
                "PHASE6_TYPE_DEFINITION_COUNT=0");
        assertFalse(source.contains("setUnits("));
        assertFalse(source.contains("rewrite"));
    }

    private static Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(".git"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }

    private static Path compositionSourceRoot() {
        return repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/composition");
    }

    private static String readJavaSources(Path root) throws IOException {
        StringBuilder source = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(file)).append('\n');
            }
        }
        return source.toString();
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }
}
