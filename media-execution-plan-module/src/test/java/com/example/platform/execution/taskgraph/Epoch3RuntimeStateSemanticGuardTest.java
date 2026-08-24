package com.example.platform.execution.taskgraph;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Repository-semantic Epoch 3 runtime-state guards for the complete static execution-plan module.
 *
 * <p>Scan evidence: recursively enumerate every {@code *.java} file under
 * {@code media-execution-plan-module/src/main/java}, remove comments and string/character literals,
 * then count the runtime-state token pattern below. This deliberately scans the complete production
 * module, rather than one named ETG/plan/compatibility source file. The same zero-result source scan
 * manifests {@code ETG_RUNTIME_STATE_FIELD_COUNT=0},
 * {@code PHYSICAL_EXECUTION_PLAN_RUNTIME_STATE_FIELD_COUNT=0}, and
 * {@code PROVIDER_COMPATIBILITY_GRAPH_RUNTIME_STATE_FIELD_COUNT=0}.
 */
class Epoch3RuntimeStateSemanticGuardTest {

    private static final Path MODULE_MAIN = repoRoot().resolve(
            "media-execution-plan-module/src/main/java");
    private static final Pattern RUNTIME_STATE_TOKEN = Pattern.compile(
            "\\b(?:HostResourceSnapshot|SchedulableCapacity|WorkerRuntimeId|"
                    + "WorkerRuntimeIncarnationId|PhysicalHostId|PhysicalHostIncarnationId|DeviceId|"
                    + "ExecutionBackend|ExecutionAssignment|Reservation|TaskLease|ExecutionAttempt|"
                    + "ExecutionOwnershipGeneration|BackendExecutionHandle|ExecutionObservation|"
                    + "ObservedUsage|ProviderProbe(?:Result|State)?|queue(?:State|Depth)?|"
                    + "probe(?:State)?|Clock|currentTime|"
                    + "currentTimeMillis|nanoTime)\\b"
                    + "|\\b(?:Instant|Clock|LocalDateTime|OffsetDateTime|ZonedDateTime)\\s*\\.\\s*"
                    + "now\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    @Test
    void etgRuntimeStateFieldCountIsZeroAcrossStaticProductionModule() {
        assertRuntimeStateCountIsZero("ETG_RUNTIME_STATE_FIELD_COUNT=0");
    }

    @Test
    void physicalExecutionPlanRuntimeStateFieldCountIsZeroAcrossStaticProductionModule() {
        assertRuntimeStateCountIsZero("PHYSICAL_EXECUTION_PLAN_RUNTIME_STATE_FIELD_COUNT=0");
    }

    @Test
    void providerCompatibilityGraphRuntimeStateFieldCountIsZeroAcrossStaticProductionModule() {
        assertRuntimeStateCountIsZero("PROVIDER_COMPATIBILITY_GRAPH_RUNTIME_STATE_FIELD_COUNT=0");
    }

    private static void assertRuntimeStateCountIsZero(String manifestedCount) {
        assertThat(runtimeStateMatches())
                .as(manifestedCount + "; scan=media-execution-plan-module/src/main/java/**/*.java")
                .isEmpty();
    }

    private static List<String> runtimeStateMatches() {
        List<String> matches = new ArrayList<>();
        for (Path path : productionJavaFiles()) {
            String executableSource = stripNonCode(read(path));
            var matcher = RUNTIME_STATE_TOKEN.matcher(executableSource);
            while (matcher.find()) {
                matches.add(repoRoot().relativize(path) + ":" + matcher.group());
            }
        }
        return List.copyOf(matches);
    }

    private static List<Path> productionJavaFiles() {
        try (var files = Files.walk(MODULE_MAIN)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot enumerate static production Java sources", exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read architecture guard source: " + path, exception);
        }
    }

    private static String stripNonCode(String source) {
        return source.replaceAll("(?s)\"\"\".*?\"\"\"", " ")
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", " ")
                .replaceAll("'(?:\\\\.|[^'\\\\])'", " ")
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }

    private static Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }
}
