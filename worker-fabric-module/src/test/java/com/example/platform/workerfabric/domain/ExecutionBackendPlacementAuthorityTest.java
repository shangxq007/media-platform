package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExecutionBackendPlacementAuthorityTest {

    @Test
    void b1NativePullMapsToPlatformManaged() {
        assertThat(ExecutionBackend.NATIVE_PULL_WORKER.placementAuthorityScope())
                .isEqualTo(PlacementAuthorityScope.PLATFORM_MANAGED);
    }

    @Test
    void b2OpenCueMapsToBackendDelegated() {
        assertThat(ExecutionBackend.OPEN_CUE_FARM.placementAuthorityScope())
                .isEqualTo(PlacementAuthorityScope.BACKEND_DELEGATED);
    }

    @Test
    void b3RemoteProviderMapsToRemoteProviderManaged() {
        assertThat(ExecutionBackend.REMOTE_PROVIDER.placementAuthorityScope())
                .isEqualTo(PlacementAuthorityScope.REMOTE_PROVIDER_MANAGED);
    }

    @Test
    void b4SameTaskCannotHaveTwoActiveBackendSelections() {
        var scenario = TaskBTestFixture.scenario("provider-a", "unit-a");
        ProviderBackendExecutionSupport support = ProviderBackendExecutionSupport.declared(
                scenario.task().providerBindingPin(),
                Set.of(ExecutionBackend.NATIVE_PULL_WORKER, ExecutionBackend.OPEN_CUE_FARM));
        ExecutionBackendSelection nativeSelection = ExecutionBackendSelection.select(
                scenario.graph(),
                scenario.task(),
                ExecutionBackendEligibilityEvaluator.evaluate(
                        scenario.task(), support, ExecutionBackend.NATIVE_PULL_WORKER));
        ExecutionBackendSelection openCueSelection = ExecutionBackendSelection.select(
                scenario.graph(),
                scenario.task(),
                ExecutionBackendEligibilityEvaluator.evaluate(
                        scenario.task(), support, ExecutionBackend.OPEN_CUE_FARM));

        assertThatIllegalArgumentException().isThrownBy(() ->
                ExecutionBackendSelectionSet.forGraph(
                        scenario.graph(), List.of(nativeSelection, openCueSelection)))
                .withMessageContaining("ONE_WORKLOAD_ONE_ACTIVE_PLACEMENT_AUTHORITY_V1");
    }

    @Test
    void b5OpenCueTaskDoesNotRequirePlatformPhysicalHostAssignment() {
        var scenario = TaskBTestFixture.scenario("provider-a", "unit-a");
        ExecutionBackendSelection selection =
                TaskBTestFixture.selection(scenario, ExecutionBackend.OPEN_CUE_FARM);

        assertThat(selection.placementAuthorityScope())
                .isEqualTo(PlacementAuthorityScope.BACKEND_DELEGATED);
        assertSelectionFieldsExclude("PhysicalHostId", "PhysicalHostDescriptor");
    }

    @Test
    void b6OpenCueTaskDoesNotRequirePlatformWorkerRuntimeAssignment() {
        TaskBTestFixture.selection(
                TaskBTestFixture.scenario("provider-a", "unit-a"),
                ExecutionBackend.OPEN_CUE_FARM);

        assertSelectionFieldsExclude(
                "WorkerRuntimeId", "WorkerRuntimeDescriptor", "WorkerRuntimeAvailability");
    }

    @Test
    void b7RemoteProviderTaskDoesNotRequireFakePhysicalHost() {
        var selection = TaskBTestFixture.selection(
                TaskBTestFixture.scenario("provider-a", "unit-a"),
                ExecutionBackend.REMOTE_PROVIDER);

        assertThat(selection.placementAuthorityScope())
                .isEqualTo(PlacementAuthorityScope.REMOTE_PROVIDER_MANAGED);
        assertSelectionFieldsExclude("PhysicalHostId", "PhysicalHostDescriptor");
    }

    @Test
    void b8RemoteProviderTaskDoesNotRequireFakeWorkerRuntime() {
        TaskBTestFixture.selection(
                TaskBTestFixture.scenario("provider-a", "unit-a"),
                ExecutionBackend.REMOTE_PROVIDER);

        assertSelectionFieldsExclude(
                "WorkerRuntimeId", "WorkerRuntimeDescriptor", "WorkerRuntimeAvailability");
    }

    @Test
    void b9RemoteProviderTaskDoesNotRequireNativeTaskLease() {
        TaskBTestFixture.selection(
                TaskBTestFixture.scenario("provider-a", "unit-a"),
                ExecutionBackend.REMOTE_PROVIDER);

        assertSelectionFieldsExclude("TaskLease", "Reservation", "ExecutionAssignment");
    }

    @Test
    void b10BackendSelectionDoesNotAlterExecutableTaskId() {
        var scenario = TaskBTestFixture.scenario("provider-a", "unit-a");
        ExecutableTaskId before = scenario.task().id();

        ExecutionBackendSelection selection =
                TaskBTestFixture.selection(scenario, ExecutionBackend.OPEN_CUE_FARM);

        assertThat(selection.executableTaskId()).isSameAs(before);
        assertThat(scenario.task().id()).isSameAs(before);
    }

    @Test
    void b11BackendSelectionDoesNotAlterEtgDigest() {
        var scenario = TaskBTestFixture.scenario("provider-a", "unit-a");
        ExecutableTaskGraphDigest before = scenario.graph().digest();

        ExecutionBackendSelectionSet selections = ExecutionBackendSelectionSet.forGraph(
                scenario.graph(),
                List.of(TaskBTestFixture.selection(scenario, ExecutionBackend.OPEN_CUE_FARM)));

        assertThat(selections.providerBoundGraph()).isSameAs(scenario.graph());
        assertThat(scenario.graph().digest()).isSameAs(before);
    }

    @Test
    void b12BackendSelectionCannotRebindProviderBindingPin() {
        var scenario = TaskBTestFixture.scenario("provider-a", "unit-a");
        var foreign = TaskBTestFixture.provider("provider-b");
        ProviderBackendExecutionSupport foreignSupport = ProviderBackendExecutionSupport.declared(
                foreign.bindingPin(), Set.of(ExecutionBackend.NATIVE_PULL_WORKER));

        assertThatIllegalArgumentException().isThrownBy(() ->
                ExecutionBackendEligibilityEvaluator.evaluate(
                        scenario.task(), foreignSupport, ExecutionBackend.NATIVE_PULL_WORKER))
                .withMessageContaining("cannot rebind");

        ExecutionBackendSelection valid =
                TaskBTestFixture.selection(scenario, ExecutionBackend.NATIVE_PULL_WORKER);
        assertThat(valid.providerBindingPin()).isSameAs(scenario.task().providerBindingPin());
    }

    @Test
    void backendEligibilityIsBoundedTypedAndUnknownFailsClosed() {
        var scenario = TaskBTestFixture.scenario("provider-a", "unit-a");
        var unsupported = ExecutionBackendEligibilityEvaluator.evaluate(
                scenario.task(),
                ProviderBackendExecutionSupport.declared(
                        scenario.task().providerBindingPin(), Set.of()),
                ExecutionBackend.OPEN_CUE_FARM);
        var unknown = ExecutionBackendEligibilityEvaluator.evaluate(
                scenario.task(),
                ProviderBackendExecutionSupport.unknown(scenario.task().providerBindingPin()),
                ExecutionBackend.OPEN_CUE_FARM);

        assertThat(unsupported.status())
                .isEqualTo(ExecutionBackendEligibilityDecision.Status.INELIGIBLE);
        assertThat(unsupported.reasons()).containsExactly(
                ExecutionBackendEligibilityReason.BACKEND_EXECUTION_MECHANICS_UNSUPPORTED);
        assertThat(unknown.status())
                .isEqualTo(ExecutionBackendEligibilityDecision.Status.UNKNOWN_FAIL_CLOSED);
        assertThat(unknown.reasons()).containsExactly(
                ExecutionBackendEligibilityReason.UNKNOWN_BACKEND_EXECUTION_SUPPORT);
        assertThatIllegalStateException().isThrownBy(() ->
                ExecutionBackendSelection.select(scenario.graph(), scenario.task(), unknown));
    }

    private static void assertSelectionFieldsExclude(String... forbiddenTypes) {
        List<String> fieldTypes = Arrays.stream(ExecutionBackendSelection.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getSimpleName)
                .toList();
        assertThat(fieldTypes).doesNotContain(forbiddenTypes);
    }
}
