package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Mechanical guards for the H1 read-only materialization boundary. */
class WorkerFabricOwnerReadArchitectureTest {

    private static final Path ROOT = repoRoot();
    private static final Path APPLICATION = ROOT.resolve(
            "worker-fabric-module/src/main/java/com/example/platform/workerfabric/application");
    private static final Path ADAPTER = ROOT.resolve(
            "worker-fabric-module/src/main/java/com/example/platform/workerfabric/infrastructure/"
                    + "JooqWorkerFabricReadAdapter.java");

    @Test
    void readAdapterHasNoLatestHeuristicMutationOrRuntimeRecomputation() throws IOException {
        String source = Files.readString(ADAPTER).toLowerCase(java.util.Locale.ROOT);

        assertThat(source)
                .doesNotContain(
                        "max(",
                        "limit 1",
                        "order by created_at desc",
                        "order by observed_at desc",
                        ".execute(",
                        "dsl.transaction",
                        "runtimedependencymatcher",
                        "runtimeeligibilityevaluator",
                        "schedulablecapacity");
        assertThat(source).contains(
                "s.snapshot_generation = g.current_generation",
                "r.worker_runtime_incarnation_id = c.current_incarnation_id",
                "where attempt_id = ?");
    }

    @Test
    void applicationReadBoundaryExposesUnknownWithoutBundleIdentity() throws IOException {
        String application;
        try (var paths = Files.walk(APPLICATION)) {
            application = paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .map(WorkerFabricOwnerReadArchitectureTest::read)
                    .reduce("", String::concat);
        }

        assertThat(application)
                .contains(
                        "Optional<WorkerRuntimeDescriptor> descriptor",
                        "Optional<DeviceDescriptor> descriptor",
                        "Optional<ProviderBindingPin> providerBindingPin",
                        "List.copyOf")
                .doesNotContain("ProviderRuntimeBundleId");
        assertThat(Files.readString(APPLICATION.resolve("package-info.java")))
                .contains("@org.springframework.modulith.NamedInterface(\"application\")");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + path, exception);
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
