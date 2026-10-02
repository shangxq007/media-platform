package com.example.platform.runtime.mediatask;

import com.example.platform.execution.binding.BoundGraphDigestMismatchException;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.BoundGraphRederivation;
import com.example.platform.execution.taskgraph.Cacheability;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.TaskCacheabilityDeriver;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.reuse.ArtifactCommitMetadata;
import com.example.platform.workerfabric.reuse.DurableOutputTarget;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopOrchestrator;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopRequest;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopResult;
import com.example.platform.workerfabric.reuse.TaskRuntimeExecution;
import com.example.platform.workerfabric.reuse.TaskRuntimeExecutionConstructionException;
import com.example.platform.workerfabric.reuse.TaskRuntimeExecutionFactory;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * P2-5b-1-R2 preparation half of the per-graph media task activity.
 *
 * <p><b>Test-wired only.</b> This class deliberately carries no Spring stereotype: it is not
 * registered in any production context (same discipline as the P2-1/P2-2/P2-3/P2-5a entry points).
 * Production registration, the worker role and the catalog-derived runtime binding map belong to
 * P2-5b-2 / P2-6.
 *
 * <p>{@link #prepareTask} performs exactly three steps, all fail-closed, and never repairs anything:
 * <ol>
 *   <li>{@link BoundGraphInputStore#load} — canonical, digest-verified rehydration of the bound inputs;</li>
 *   <li>{@link BoundGraphRederivation#rederive} — deterministic re-derivation of the executable task graph;</li>
 *   <li>digest re-verification — the re-derived graph digest must equal both the stored expectation and
 *       the caller's {@link BoundGraphReference} expectation.</li>
 * </ol>
 *
 * <p>{@link #executePreparedGraph} is the execution half (P2-5b-2a-2b): it constructs one
 * {@link TaskRuntimeExecution} per task from the current grant and the caller's publication intent,
 * derives the per-task cacheability, and drives one whole-graph
 * {@link RuntimeClosedLoopOrchestrator#execute} call. The Temporal method
 * {@code executeTask(PreparedTaskRef, tenantId)} still belongs to P2-5b-2: it needs the worker role,
 * the catalog-derived runtime binding map, the orchestrator construction and a production authority
 * for the output publication intent, none of which exist yet.
 */
public final class MediaTaskActivity {

    private final BoundGraphInputStore boundGraphInputStore;

    public MediaTaskActivity(BoundGraphInputStore boundGraphInputStore) {
        this.boundGraphInputStore = Objects.requireNonNull(
                boundGraphInputStore, "boundGraphInputStore");
    }

    /**
     * Loads, re-derives and digest-verifies the bound graph addressed by {@code reference}.
     *
     * @throws IllegalArgumentException          when the tenant scope does not match, or no record
     *                                           exists for the reference
     * @throws BoundGraphDigestMismatchException when the stored or re-derived digest diverges
     */
    public PreparedTask prepareTask(BoundGraphReference reference, String tenantId) {
        Objects.requireNonNull(reference, "reference");
        requireMatchingTenant(reference, tenantId);

        BoundGraphInputs inputs = boundGraphInputStore.load(reference);

        ProviderBoundExecutableTaskGraph graph = BoundGraphRederivation.rederive(inputs);

        String rederived = graph.digest().sha256Hex();
        if (!rederived.equals(reference.expectedExecutableTaskGraphDigest())) {
            throw new BoundGraphDigestMismatchException(
                    reference.expectedExecutableTaskGraphDigest(), rederived);
        }
        String storedExpectation = inputs.expectedExecutableTaskGraphDigest().sha256Hex();
        if (!rederived.equals(storedExpectation)) {
            throw new BoundGraphDigestMismatchException(storedExpectation, rederived);
        }
        return new PreparedTask(graph, inputs, graph.digest());
    }

    private static void requireMatchingTenant(BoundGraphReference reference, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!tenantId.equals(reference.tenantId())) {
            throw new IllegalArgumentException(
                    "tenantId must match the bound graph reference scope");
        }
    }

    /**
     * Executes every task of an already-prepared graph through the closed loop.
     *
     * <p>Pure orchestration: the caller owns the grant lookup, the storage/artifact publication
     * intent and the orchestrator (with its catalog-derived runtime bindings), so nothing is invented
     * here. Every task must have a current grant and a publication intent.
     *
     * @throws TaskRuntimeExecutionConstructionException when a task has no current grant or no
     *     publication intent, or when its grant does not bind the task
     */
    public RuntimeClosedLoopResult executePreparedGraph(
            String tenantId,
            PreparedTask prepared,
            Map<ExecutableTaskId, AssignmentGrant> currentGrants,
            Map<ExecutableTaskId, DurableOutputTarget> durableOutputTargets,
            Map<ExecutableTaskId, ArtifactCommitMetadata> artifactCommitMetadata,
            RuntimeClosedLoopOrchestrator orchestrator) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(orchestrator, "orchestrator");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(currentGrants, "currentGrants");
        Objects.requireNonNull(durableOutputTargets, "durableOutputTargets");
        Objects.requireNonNull(artifactCommitMetadata, "artifactCommitMetadata");

        ProviderBoundExecutableTaskGraph graph = prepared.executableTaskGraph();
        Set<ExecutableTaskId> requestedTasks = new LinkedHashSet<>();
        Map<ExecutableTaskId, TaskRuntimeExecution> taskExecutions = new LinkedHashMap<>();
        for (ExecutableTask task : graph.tasks()) {
            requestedTasks.add(task.id());
            taskExecutions.put(task.id(), TaskRuntimeExecutionFactory.build(
                    task,
                    requireEntry(currentGrants, task.id(), "GRANT_ABSENT",
                            "no current grant exists for this task"),
                    requireEntry(durableOutputTargets, task.id(), "DURABLE_OUTPUT_TARGET_ABSENT",
                            "no durable output target was supplied for this task"),
                    requireEntry(artifactCommitMetadata, task.id(), "ARTIFACT_COMMIT_METADATA_ABSENT",
                            "no artifact commit metadata was supplied for this task")));
        }
        Map<ExecutableTaskId, Cacheability> cacheability =
                TaskCacheabilityDeriver.derive(graph);
        return orchestrator.execute(new RuntimeClosedLoopRequest(
                tenantId, graph, requestedTasks, cacheability, taskExecutions));
    }

    private static <T> T requireEntry(
            Map<ExecutableTaskId, T> values, ExecutableTaskId taskId, String code, String detail) {
        T value = values.get(taskId);
        if (value == null) {
            throw new TaskRuntimeExecutionConstructionException(code, detail);
        }
        return value;
    }
}
