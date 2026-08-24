package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.shared.identity.ArtifactId;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mechanical zero guards and typed identity proof for Roadmap #22 Epoch 3 Task D. */
class TaskDArchitectureGuardTest {

    @Test
    void executionAssignmentProviderRebindCountIsZero() {
        Set<Class<?>> componentTypes = Arrays.stream(ExecutionAssignment.class.getRecordComponents())
                .map(RecordComponent::getType)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertThat(componentTypes).doesNotContain(ProviderBindingPin.class);
        assertThat(Arrays.stream(ExecutionAssignment.class.getRecordComponents())
                .map(RecordComponent::getName))
                .noneMatch(name -> name.matches("(?i).*(provider|binding|implementation).*"));
    }

    @Test
    void attemptWithoutGenerationCountIsZero() {
        assertThat(Arrays.stream(ExecutionAttempt.class.getRecordComponents())
                .map(RecordComponent::getType))
                .contains(ExecutionOwnershipGeneration.class);
        assertThat(Arrays.stream(ExecutionAttempt.class.getRecordComponents())
                .map(RecordComponent::getName))
                .noneMatch(name -> name.matches("(?i).*(lease|admission).*"));
        assertThat(ExecutionAttemptState.values()).containsExactly(
                ExecutionAttemptState.CREATED,
                ExecutionAttemptState.RUNNING,
                ExecutionAttemptState.SUCCEEDED,
                ExecutionAttemptState.FAILED,
                ExecutionAttemptState.CANCELLED,
                ExecutionAttemptState.ABANDONED);
    }

    @Test
    void taskLeaseDuplicatesFullRuntimeObjectsCountIsZero() {
        assertThat(Arrays.stream(TaskLease.class.getRecordComponents())
                .map(RecordComponent::getType))
                .doesNotContain(
                        Reservation.class,
                        PhysicalHostDescriptor.class,
                        WorkerRuntimeDescriptor.class);
    }

    @Test
    void allGrantAndArtifactIdentityTypesAreDistinct() {
        assertThat(Set.of(
                ExecutableTaskId.class,
                ExecutionAssignmentId.class,
                ReservationId.class,
                ExecutionAttemptId.class,
                LeaseId.class,
                ArtifactId.class)).hasSize(6);

        Object executableTaskId = new ExecutableTaskId("1".repeat(64));
        Object assignmentId = new ExecutionAssignmentId("assignment-identity");
        Object reservationId = new ReservationId("reservation-identity");
        Object attemptId = new ExecutionAttemptId("attempt-identity");
        Object leaseId = new LeaseId("lease-identity");
        Object artifactId = new ArtifactId("artifact-identity");
        assertThat(Set.of(
                executableTaskId,
                assignmentId,
                reservationId,
                attemptId,
                leaseId,
                artifactId)).hasSize(6);
    }

    @Test
    void duplicateActiveNativeLeaseCountIsZero() throws Exception {
        String schema = Files.readString(repoRoot().resolve(
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"));
        assertThat(schema)
                .contains("create unique index ux_wf_one_active_native_lease_per_task")
                .contains("on wf_task_lease (task_id) where active");
    }

    @Test
    void staleGenerationWriteAcceptanceCountIsZero() throws Exception {
        String adapter = Files.readString(repoRoot().resolve(
                "worker-fabric-module/src/main/java/com/example/platform/workerfabric/infrastructure/"
                        + "JooqAtomicAssignmentGrantBoundary.java"));
        assertThat(adapter)
                .contains("current_generation = a.generation")
                .contains("current_attempt_id = a.attempt_id")
                .contains("StaleOwnershipGenerationException");
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
