package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mechanical zero guards for Roadmap #22 Epoch 3 Task A authority boundaries. */
class TaskAArchitectureGuardTest {

    private static final List<String> STATIC_LAYER_RUNTIME_TOKENS = List.of(
            "HostResourceSnapshot",
            "HostResourceSnapshotGeneration",
            "HostResourceSnapshotFreshness",
            "HostResourceAgent",
            "WorkerRuntimeReporterRef",
            "ObservedUsage",
            "SchedulableCapacity",
            "PhysicalHostAvailability",
            "WorkerRuntimeAvailability");

    @Test
    void hostResourceSnapshotAlwaysCarriesStableAndIncarnationHostIdentity() {
        assertThat(Arrays.stream(HostResourceSnapshot.class.getRecordComponents())
                        .map(component -> component.getType().getSimpleName()))
                .containsSequence("PhysicalHostId", "PhysicalHostIncarnationId")
                .contains(
                        "HostResourceSnapshotGeneration",
                        "HostResourceSnapshotSchemaVersion",
                        "CapacitySnapshot",
                        "ObservedUsage");
    }

    @Test
    void schedulableCapacityIsHostScopedAndNeverWorkerRuntimeScoped() {
        List<String> componentTypes = Arrays.stream(SchedulableCapacity.class.getRecordComponents())
                .map(component -> component.getType().getSimpleName())
                .toList();

        assertThat(componentTypes)
                .contains("PhysicalHostId", "PhysicalHostIncarnationId")
                .doesNotContain(
                        "WorkerRuntimeId",
                        "WorkerRuntimeIncarnationId",
                        "WorkerRuntimeAvailability",
                        "WorkerRuntimeReporterRef");
    }

    @Test
    void snapshotFreshnessTimeoutIsRuntimePolicyNotSnapshotOrCapacitySemantics() {
        assertThat(Arrays.stream(HostResourceSnapshot.class.getRecordComponents())
                        .map(component -> component.getType().getSimpleName()))
                .doesNotContain("Duration", "HostResourceSnapshotFreshnessPolicy");
        assertThat(Arrays.stream(SchedulableCapacity.class.getRecordComponents())
                        .map(component -> component.getType().getSimpleName()))
                .doesNotContain("Duration", "HostResourceSnapshotFreshnessPolicy");
    }

    @Test
    void hostResourceAgentExposesEvidenceProductionOnly() {
        Set<String> publicDeclaredMethods = Arrays.stream(HostResourceAgent.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(publicDeclaredMethods).containsExactly("capture");
        assertThat(publicDeclaredMethods).doesNotContain(
                "schedule",
                "selectTask",
                "selectProvider",
                "reserve",
                "releaseReservation",
                "createAttempt");
    }

    @Test
    void etgTaskAndPhysicalPlanProductionSourcesContainNoTaskARuntimeState() throws IOException {
        Path staticLayer = repoRoot().resolve("media-execution-plan-module/src/main/java");
        String productionSource;
        try (var files = Files.walk(staticLayer)) {
            productionSource = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .map(TaskAArchitectureGuardTest::read)
                    .collect(java.util.stream.Collectors.joining("\n"));
        }

        assertThat(STATIC_LAYER_RUNTIME_TOKENS)
                .allSatisfy(token -> assertThat(productionSource).doesNotContain(token));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read architecture guard source: " + path, exception);
        }
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
