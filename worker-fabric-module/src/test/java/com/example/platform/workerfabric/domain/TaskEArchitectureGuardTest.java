package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.BackendLocalRetry;
import com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseOwnershipFence;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mechanical Task E zero guards complementing the executable L1-L10/A1-A9 proofs. */
class TaskEArchitectureGuardTest {

    @Test
    void leaseExpiryImmediateResourceReleaseCountIsZero() throws Exception {
        String expiry = lifecycleMethod("expireLease", "disconnectWorker");
        assertThat(expiry)
                .contains("ReservationState.RECOVERY_HOLD")
                .doesNotContain("ReservationState.RELEASED");
    }

    @Test
    void recoveryHoldSchedulableCapacityCountIsZero() {
        assertThat(new Reservation(
                        ReservationId.of("guard-reservation"),
                        PhysicalHostId.of("guard-host"),
                        ReservationKind.TASK,
                        ReservedResources.none(),
                        ReservationState.RECOVERY_HOLD)
                .keepsCapacityUnavailable()).isTrue();
        assertThat(Arrays.stream(ReservationState.values())
                .filter(state -> state == ReservationState.RECOVERY_HOLD))
                .hasSize(1);
    }

    @Test
    void staleGenerationAuthoritativeCompletionCountIsZero() throws Exception {
        String grantAuthority = Files.readString(repoRoot().resolve(
                "worker-fabric-module/src/main/java/com/example/platform/workerfabric/"
                        + "infrastructure/JooqAtomicAssignmentGrantBoundary.java"));
        assertThat(grantAuthority)
                .contains("o.current_attempt_id = a.attempt_id")
                .contains("o.current_generation = a.generation")
                .contains("StaleOwnershipGenerationException");
    }

    @Test
    void duplicateActiveReservationCountIsZero() throws Exception {
        String schema = Files.readString(repoRoot().resolve(
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"));
        assertThat(schema)
                .contains("ux_wf_one_active_reservation_per_task")
                .contains("on wf_reservation (task_id) where state = 'ACTIVE'");
    }

    @Test
    void staleAdmissionResponseAcceptanceCountIsZero() {
        List<Class<?>> fenceComponents = Arrays.stream(
                        LeaseOwnershipFence.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();
        assertThat(fenceComponents).contains(
                ExecutableTaskId.class,
                ExecutionAssignmentId.class,
                ExecutionAttemptId.class,
                ExecutionOwnershipGeneration.class,
                WorkerRuntimeIncarnationId.class,
                LeaseFencingToken.class);
    }

    @Test
    void retryChangesExecutableTaskIdCountIsZero() throws Exception {
        String grant = Files.readString(repoRoot().resolve(
                "worker-fabric-module/src/main/java/com/example/platform/workerfabric/"
                        + "infrastructure/JooqAtomicAssignmentGrantBoundary.java"));
        assertThat(grant)
                .contains("String taskId = command.executableTask().id().sha256Hex()")
                .doesNotContain("new ExecutableTaskId(unique(");
    }

    @Test
    void backendLocalRetryCreatesPlatformAttemptCountIsZero() throws Exception {
        assertThat(Arrays.stream(BackendLocalRetry.class.getRecordComponents())
                .map(RecordComponent::getType)).doesNotContain(ExecutableTaskId.class);
        String method = lifecycleMethod("recordBackendLocalRetry", "heartbeatResult");
        assertThat(method)
                .contains("ACKNOWLEDGED_WITHOUT_PLATFORM_ATTEMPT")
                .doesNotContain("wf_execution_attempt", "ExecutionAttemptId(");
    }

    private static String lifecycleMethod(String startMethod, String nextMethod) throws Exception {
        String source = Files.readString(repoRoot().resolve(
                "worker-fabric-module/src/main/java/com/example/platform/workerfabric/"
                        + "infrastructure/JooqExecutionLifecycleBoundary.java"));
        int startName = source.indexOf(startMethod);
        int endName = source.indexOf(nextMethod, startName + startMethod.length());
        int start = source.lastIndexOf("\n    ", startName);
        int end = source.lastIndexOf("\n    ", endName);
        if (start < 0 || end <= start) {
            throw new IllegalStateException("lifecycle method source boundary not found");
        }
        return source.substring(start, end);
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
