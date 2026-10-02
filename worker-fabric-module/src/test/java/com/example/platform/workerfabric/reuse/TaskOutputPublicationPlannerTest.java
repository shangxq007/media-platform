package com.example.platform.workerfabric.reuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.infrastructure.JooqAtomicAssignmentGrantBoundary;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * TASK-OUTPUT-PUBLICATION-PLANNER-001: the planner derives a deterministic publication intent from
 * the exact task plus the caller's scope/storage context, and fails closed when either is
 * insufficient. Also proves the widened grant boundary declares {@code findCurrentGrant}.
 */
class TaskOutputPublicationPlannerTest {

    private static final ExecutableTaskId TASK_ID = new ExecutableTaskId("c".repeat(64));
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Test
    void plansTheDurableTargetAndTheArtifactMetadataDeterministically() {
        ExecutableTask task = task();
        TaskOutputPublicationContext context = context();

        TaskOutputPublicationPlan plan = TaskOutputPublicationPlanner.plan(task, context);

        DurableOutputTarget target = plan.durableOutputTarget();
        assertThat(target.providerId()).isEqualTo(new StorageProviderId("phase-planner-storage"));
        assertThat(target.namespace()).isEqualTo(new StorageNamespace(
                "tenant-planner", "project-planner", NamespaceClass.DERIVED,
                RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL));
        assertThat(target.writeSessionId()).isEqualTo("task-output-" + TASK_ID.sha256Hex());

        ArtifactCommitMetadata metadata = plan.artifactCommitMetadata();
        assertThat(metadata.artifactId()).isEqualTo(new ArtifactId("render-task-" + TASK_ID.sha256Hex()));
        assertThat(metadata.tenantId()).isEqualTo("tenant-planner");
        assertThat(metadata.mediaType()).isEqualTo(ArtifactMediaType.VIDEO);
        assertThat(metadata.artifactKind()).isEqualTo(ArtifactKind.RENDER_MASTER);
        assertThat(metadata.schemaVersion()).isEqualTo(Artifact.CURRENT_SCHEMA_VERSION);
        assertThat(metadata.replicaRole()).isEqualTo(ReplicaRole.PRIMARY);
        assertThat(metadata.region()).isEqualTo("local");
        // No pre-execution provenance authority exists for task outputs; nothing is fabricated.
        assertThat(metadata.provenanceDeclarations()).isEmpty();
        assertThat(metadata.evaluatedAt()).isEqualTo(NOW);
        assertThat(metadata.createdAt()).isEqualTo(NOW);
        assertThat(metadata.renderJobId()).isEqualTo("render-job-planner");
        assertThat(metadata.projectId()).isEqualTo("project-planner");
    }

    @Test
    void planningTheSameTaskTwiceIsIdentical() {
        ExecutableTask task = task();

        assertThat(TaskOutputPublicationPlanner.plan(task, context()))
                .isEqualTo(TaskOutputPublicationPlanner.plan(task, context()));
    }

    @Test
    void failsClosedWhenTheTaskDeclaresNoAuthoritativeOutput() {
        ExecutableTask withoutOutputs = mock(ExecutableTask.class);
        when(withoutOutputs.id()).thenReturn(TASK_ID);
        when(withoutOutputs.authoritativeOutputIds()).thenReturn(List.of());

        assertThatThrownBy(() -> TaskOutputPublicationPlanner.plan(withoutOutputs, context()))
                .isInstanceOf(TaskOutputPublicationPlanningException.class)
                .hasMessageContaining("TASK_OUTPUT_ABSENT");
    }

    @Test
    void rejectsAnIncompleteContext() {
        assertThatThrownBy(() -> new TaskOutputPublicationContext(
                " ", "render-job-planner", "project-planner",
                new StorageProviderId("phase-planner-storage"), "local",
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new TaskOutputPublicationContext(
                "tenant-planner", "render-job-planner", "project-planner",
                new StorageProviderId("phase-planner-storage"), " ",
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region");

        assertThatThrownBy(() -> TaskOutputPublicationPlanner.plan(null, context()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaskOutputPublicationPlanner.plan(task(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theGrantBoundaryDeclaresFindCurrentGrantAndTheJooqAuthorityMatchesIt() throws Exception {
        Method declared = AtomicAssignmentGrantBoundary.class.getMethod(
                "findCurrentGrant", ExecutableTaskId.class);
        Method implemented = JooqAtomicAssignmentGrantBoundary.class.getMethod(
                "findCurrentGrant", ExecutableTaskId.class);

        assertThat(declared.getReturnType()).isEqualTo(Optional.class);
        assertThat(declared.isDefault()).isFalse();
        assertThat(implemented.getGenericReturnType()).isEqualTo(declared.getGenericReturnType());
        assertThat(declared.getDeclaringClass()).isEqualTo(AtomicAssignmentGrantBoundary.class);
    }

    // ---------- TEST-ONLY fixtures ----------

    private static ExecutableTask task() {
        ExecutableTask task = mock(ExecutableTask.class);
        when(task.id()).thenReturn(TASK_ID);
        when(task.authoritativeOutputIds())
                .thenReturn(List.of(new ExecutionOutputId("output-1")));
        return task;
    }

    private static TaskOutputPublicationContext context() {
        return new TaskOutputPublicationContext(
                "tenant-planner",
                "render-job-planner",
                "project-planner",
                new StorageProviderId("phase-planner-storage"),
                "local",
                ArtifactMediaType.VIDEO,
                ArtifactKind.RENDER_MASTER,
                NOW);
    }
}
