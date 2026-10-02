package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import java.util.List;
import java.util.Objects;

/**
 * TASK-OUTPUT-PUBLICATION-PLANNER-001: the production authority that decides the publication intent
 * of one task output — the {@link DurableOutputTarget} and the {@link ArtifactCommitMetadata} the
 * closed loop publishes through.
 *
 * <p>Pure and stateless. Every value is either derived deterministically from the exact task or
 * taken from the caller's {@link TaskOutputPublicationContext}; nothing is invented and nothing is
 * random, so a replay of the same task plans the same intent:
 * <ul>
 *   <li>artifact identity — {@code render-task-<executableTaskId>}, the deterministic
 *       platform-issued identity for one task's output (the same style as the composition
 *       materializer's {@code composition-<idempotencyKey>});</li>
 *   <li>write session — {@code task-output-<executableTaskId>}, stable so an idempotent retry resumes
 *       the same storage write instead of creating a second object;</li>
 *   <li>storage namespace — {@code (tenantId, projectId, DERIVED, SINGLE_REGION, INTERNAL)}, the exact
 *       rule {@code StorageOutputService} applies to every derived output;</li>
 *   <li>schema version — {@link Artifact#CURRENT_SCHEMA_VERSION}; replica role — {@code PRIMARY} (one
 *       authoritative replica of a freshly committed output);</li>
 *   <li>provenance — empty, exactly as the other production artifact-commit path
 *       ({@code OwnerPortCompositionMaterialization}) publishes: no pre-execution provenance
 *       authority exists for task outputs (edges need the attempt and the request/result digests,
 *       which only exist after execution);</li>
 *   <li>timestamps — the caller's single clock reading.</li>
 * </ul>
 *
 * <p><b>Fail closed.</b> A task that declares no authoritative output cannot have a publication
 * intent, and a context missing any scope or storage value is rejected before planning; both raise
 * {@link TaskOutputPublicationPlanningException} (or the context's own construction error) rather
 * than producing a partially-specified intent.
 */
public final class TaskOutputPublicationPlanner {

    private TaskOutputPublicationPlanner() {
    }

    /**
     * Plans the publication intent for one task's authoritative output.
     *
     * @throws TaskOutputPublicationPlanningException when the task declares no authoritative output
     */
    public static TaskOutputPublicationPlan plan(
            ExecutableTask task, TaskOutputPublicationContext context) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(context, "context");
        if (task.authoritativeOutputIds().isEmpty()) {
            throw new TaskOutputPublicationPlanningException(
                    "TASK_OUTPUT_ABSENT",
                    "a task without an authoritative output has no publication intent");
        }
        String taskId = task.id().sha256Hex();
        StorageNamespace namespace = new StorageNamespace(
                context.tenantId(),
                context.projectId(),
                NamespaceClass.DERIVED,
                RegionPolicy.SINGLE_REGION,
                DataClassification.INTERNAL);
        DurableOutputTarget durableOutputTarget = new DurableOutputTarget(
                context.storageProviderId(), namespace, "task-output-" + taskId);
        ArtifactCommitMetadata artifactCommitMetadata = new ArtifactCommitMetadata(
                new ArtifactId("render-task-" + taskId),
                context.tenantId(),
                context.mediaType(),
                context.artifactKind(),
                Artifact.CURRENT_SCHEMA_VERSION,
                ReplicaRole.PRIMARY,
                context.region(),
                List.of(),
                context.evaluatedAt(),
                context.evaluatedAt(),
                context.renderJobId(),
                context.projectId());
        return new TaskOutputPublicationPlan(durableOutputTarget, artifactCommitMetadata);
    }
}
