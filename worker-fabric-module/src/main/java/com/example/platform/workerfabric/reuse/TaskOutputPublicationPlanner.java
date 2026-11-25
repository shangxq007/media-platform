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
 *   <li>artifact identity — {@code render-task-<first-52-hex-of-executableTaskId>}, the deterministic
 *       platform-issued identity for one task's output (the same style as the composition
 *       materializer's {@code composition-<idempotencyKey>}). The 64-hex task id is truncated to its
 *       first 52 hex characters so the id is exactly 64 chars, matching {@code artifact.id}
 *       {@code varchar(64)} (P1-1A.10/P1-1A.11, Option A2);</li>
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

    /** Map the compiled terminal output to the Artifact classification owned by publication. */
    public static com.example.platform.artifact.domain.ArtifactKind renderArtifactKind(ExecutableTask task) {
        var roles = task.memberships().stream().flatMap(member -> member.physicalPlanUnit().typedOutputs().stream())
                .flatMap(output -> output.finalArtifactExpectations().stream()).map(expectation -> expectation.role()).toList();
        if (roles.isEmpty()) return com.example.platform.artifact.domain.ArtifactKind.DERIVED_MEDIA;
        if (roles.size() != 1) throw new IllegalArgumentException("A native task must own one typed result");
        return switch (roles.getFirst()) {
            case RENDER_MASTER -> com.example.platform.artifact.domain.ArtifactKind.RENDER_MASTER;
            case DELIVERY_RENDITION -> com.example.platform.artifact.domain.ArtifactKind.DELIVERY_RENDITION;
            case DERIVED_MEDIA -> com.example.platform.artifact.domain.ArtifactKind.DERIVED_MEDIA;
        };
    }

    private TaskOutputPublicationPlanner() {
    }
    /** Production publication binds the complete owning run and fenced task attempt. */
    public static TaskOutputPublicationPlan plan(ExecutableTask task, TaskOutputPublicationContext context,
            String attemptId,long generation) {
        if(attemptId==null||attemptId.isBlank()||generation<1)throw new IllegalArgumentException("fenced task attempt required");
        var basic=plan(task,context);
        String identity=context.tenantId()+"\u0000"+context.projectId()+"\u0000"+context.producingIdentity()+"\u0000"+task.id().sha256Hex()+"\u0000"+attemptId+"\u0000"+generation;
        String key;
        try {key=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        var m=basic.artifactCommitMetadata();
        return new TaskOutputPublicationPlan(new DurableOutputTarget(basic.durableOutputTarget().providerId(),basic.durableOutputTarget().namespace(),"task-output-"+key),
            new ArtifactCommitMetadata(new ArtifactId((context.renderJobId()==null?"exec-output-":"render-task-")+key.substring(0,52)),m.tenantId(),m.mediaType(),m.artifactKind(),m.schemaVersion(),m.replicaRole(),m.region(),m.provenanceDeclarations(),m.evaluatedAt(),m.createdAt(),m.renderJobId(),m.projectId()));
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
                // P1-1A.11 (Option A2): exactly 64 chars to fit artifact.id varchar(64). The task id is
                // a validated 64-hex string (ExecutableTaskId), so the first 52 hex chars are a safe,
                // deterministic prefix (208 bits; collision-negligible) and the id stays content-derived.
                new ArtifactId("render-task-" + taskId.substring(0, 52)),
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
