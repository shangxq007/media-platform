package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Mechanical zero guards for Roadmap #22 Epoch 3 Task B authority boundaries. */
class TaskBArchitectureGuardTest {

    @Test
    void placementMappingIsClosedTypedAndNotCallerMutable() {
        assertThat(ExecutionBackend.values()).containsExactly(
                ExecutionBackend.NATIVE_PULL_WORKER,
                ExecutionBackend.OPEN_CUE_FARM,
                ExecutionBackend.REMOTE_PROVIDER);
        assertThat(PlacementAuthorityScope.values()).containsExactly(
                PlacementAuthorityScope.PLATFORM_MANAGED,
                PlacementAuthorityScope.BACKEND_DELEGATED,
                PlacementAuthorityScope.REMOTE_PROVIDER_MANAGED);
        assertThat(Arrays.stream(ExecutionBackend.class.getDeclaredFields())
                        .filter(field -> !field.isEnumConstant())
                        .filter(field -> !field.isSynthetic()))
                .allSatisfy(field -> assertThat(Modifier.isFinal(field.getModifiers())).isTrue());
    }

    @Test
    void backendSelectionHasNoPlacementAssignmentLeaseOrProviderRebindingAuthority() {
        Set<String> selectionFieldTypes = Arrays.stream(
                        ExecutionBackendSelection.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getSimpleName)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(selectionFieldTypes)
                .containsExactlyInAnyOrder("ExecutableTask", "ExecutionBackend")
                .doesNotContain(
                        "PhysicalHostId",
                        "PhysicalHostDescriptor",
                        "WorkerRuntimeId",
                        "WorkerRuntimeDescriptor",
                        "TaskLease",
                        "Reservation",
                        "ExecutionAssignment",
                        "ProviderBindingPin",
                        "ProviderImplementationId");
        assertThat(Arrays.stream(ExecutionBackendSelection.class.getDeclaredConstructors())
                        .map(Constructor::getModifiers))
                .allSatisfy(modifiers -> assertThat(Modifier.isPrivate(modifiers)).isTrue());
    }

    @Test
    void selectionSetIsTheOnlyOnePerTaskConstructionPathAndIsImmutable() {
        List<String> publicMethods = Arrays.stream(ExecutionBackendSelectionSet.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(publicMethods).containsExactly("forGraph", "providerBoundGraph", "selections");
        assertThat(Arrays.stream(ExecutionBackendSelectionSet.class.getDeclaredFields()))
                .allSatisfy(field -> assertThat(Modifier.isFinal(field.getModifiers())).isTrue());
    }

    @Test
    void runtimeEligibilityAlgebraFreezesCompletePhase20ReasonsInEnumOrder() {
        assertThat(RuntimeEligibilityReason.values()).containsExactly(
                RuntimeEligibilityReason.RUNTIME_SUPPORT_REQUIREMENT_MISSING,
                RuntimeEligibilityReason.RUNTIME_SUPPORT_ADVERTISEMENT_MISSING,
                RuntimeEligibilityReason.RUNTIME_SUPPORT_MISMATCH,
                RuntimeEligibilityReason.RUNTIME_SUPPORT_UNSUPPORTED,
                RuntimeEligibilityReason.PROBE_UNKNOWN,
                RuntimeEligibilityReason.PROBE_STALE,
                RuntimeEligibilityReason.PROBE_FAILED,
                RuntimeEligibilityReason.NO_ELIGIBLE_WORKER,
                RuntimeEligibilityReason.NO_ELIGIBLE_DEVICE,
                RuntimeEligibilityReason.WORKER_UNAVAILABLE,
                RuntimeEligibilityReason.HOST_UNAVAILABLE,
                RuntimeEligibilityReason.DEVICE_UNAVAILABLE,
                RuntimeEligibilityReason.RUNTIME_UNAVAILABLE,
                RuntimeEligibilityReason.SANDBOX_RUNTIME_UNAVAILABLE,
                RuntimeEligibilityReason.INSUFFICIENT_CURRENT_RESOURCE,
                RuntimeEligibilityReason.STALE_HOST_RESOURCE_SNAPSHOT,
                RuntimeEligibilityReason.HOST_INCARNATION_MISMATCH,
                RuntimeEligibilityReason.RUNTIME_INCARNATION_MISMATCH,
                RuntimeEligibilityReason.RESERVATION_CONFLICT,
                RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_INCOMPLETE_CRITICAL_EVIDENCE,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_PROVIDER_IMPLEMENTATION_MISMATCH,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_WORKER_RUNTIME_MISMATCH,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_PHYSICAL_HOST_MISMATCH,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_IDENTITY_MISMATCH,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_STALE_OBSERVATION,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_PROBE_UNKNOWN,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_PROBE_FAILED,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_RUNTIME_UNAVAILABLE,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_CPU_ARCHITECTURE_INCOMPATIBLE,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_CLASS_UNAVAILABLE,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_UNAVAILABLE,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_DRIVER_RUNTIME_INCOMPATIBLE,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_PROVIDER_BUILD_FEATURE_MISSING,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_CODEC_OR_FILTER_FEATURE_MISSING,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_FEATURE_UNAVAILABLE,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_NOT_EXPOSED,
                RuntimeEligibilityReason.PROVIDER_HARDWARE_SANDBOX_PERMISSION_UNAVAILABLE,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_INCOMPLETE_CRITICAL_EVIDENCE,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_PROBE_SCHEMA_MISMATCH,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_PROVIDER_IMPLEMENTATION_MISMATCH,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_WORKER_RUNTIME_MISMATCH,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_DEVICE_BINDING_MISMATCH,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_STALE_OBSERVATION,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_MISSING,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_VERSION_INCOMPATIBLE,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_ABI_INCOMPATIBLE,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_FEATURE_MISSING,
                RuntimeEligibilityReason.RUNTIME_DEPENDENCY_BUILD_RUNTIME_FLAG_MISSING);
    }

    @Test
    void staticCanonicalSourcesRemainBackendAndRuntimeStateFree() throws IOException {
        Path staticLayer = repoRoot().resolve("media-execution-plan-module/src/main/java");
        String productionSource;
        try (var files = Files.walk(staticLayer)) {
            productionSource = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .map(TaskBArchitectureGuardTest::read)
                    .collect(Collectors.joining("\n"));
        }

        assertThat(productionSource)
                .doesNotContain("ExecutionBackendSelection")
                .doesNotContain("PlacementAuthorityScope")
                .doesNotContain("RuntimeEligibilityDecision")
                .doesNotContain("ProviderProbeResult")
                .doesNotContain("SchedulableCapacity");
    }

    @Test
    void taskBDefinesNoRegistryOptimizerMatcherLeaseAttemptOrBackendIntegration() throws IOException {
        Path domain = repoRoot().resolve(
                "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain");
        List<String> taskBTypes = List.of(
                "ExecutionBackend",
                "ExecutionBackendEligibilityDecision",
                "ExecutionBackendEligibilityEvaluator",
                "ExecutionBackendEligibilityReason",
                "ExecutionBackendSelection",
                "ExecutionBackendSelectionSet",
                "NativeRuntimeEligibilityRequest",
                "PlacementAuthorityScope",
                "ProviderBackendExecutionSupport",
                "ProviderProbeRequirement",
                "ProviderProbeResult",
                "ReservationFeasibility",
                "RuntimeEligibilityDecision",
                "RuntimeEligibilityEvaluator",
                "RuntimeEligibilityReason",
                "RuntimeEnvironmentAvailability",
                "RuntimeResourceDemand",
                "SandboxRuntimeAvailability",
                "SandboxRuntimeRequirement");
        String taskBSource = taskBTypes.stream()
                .map(type -> read(domain.resolve(type + ".java")))
                .collect(Collectors.joining("\n"));

        assertThat(taskBSource)
                .doesNotContain(
                        "CentralWorkMatcher",
                        "RequestWork",
                        "TaskLease",
                        "ExecutionAttempt",
                        "ExecutionAssignment",
                        "OpenCueAdapter",
                        "RemoteProviderAdapter",
                        "Optimizer",
                        "Registry");
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
