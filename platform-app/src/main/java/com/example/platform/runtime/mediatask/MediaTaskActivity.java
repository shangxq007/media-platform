package com.example.platform.runtime.mediatask;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
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
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowResult;
import com.example.platform.workflow.temporal.mediatask.PreparedTaskRef;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.reuse.AdmissionCapacityRetryableException;
import com.example.platform.workerfabric.reuse.ArtifactCommitMetadata;
import com.example.platform.workerfabric.reuse.DurableOutputTarget;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopOrchestrator;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopRequest;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopResult;
import com.example.platform.workerfabric.reuse.TaskRuntimeExecution;
import com.example.platform.workerfabric.reuse.TaskRuntimeExecutionConstructionException;
import com.example.platform.workerfabric.reuse.TaskRuntimeExecutionFactory;
import com.example.platform.workerfabric.reuse.TaskOutputPublicationContext;
import com.example.platform.workerfabric.reuse.TaskOutputPublicationPlan;
import com.example.platform.workerfabric.reuse.TaskOutputPublicationPlanner;
import io.temporal.activity.Activity;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Set;

/** Worker orchestration over verified bound-graph inputs and existing Execution/Artifact contracts.
 * The Temporal adapter owns calls into Render's public lifecycle. This component does not write
 * Render state, bypass native fencing, choose an arbitrary final row or create another planner. */
public final class MediaTaskActivity {

    private final BoundGraphInputStore boundGraphInputStore;
    private final AtomicAssignmentGrantBoundary grantBoundary;
    private final RuntimeClosedLoopOrchestrator orchestrator;
    private final MediaTaskPublicationSettings publicationSettings;
    private final FirstRenderGrantMinter grantMinter;
    private final ProviderPluginCatalog providerPluginCatalog;

    public MediaTaskActivity(
            BoundGraphInputStore boundGraphInputStore,
            AtomicAssignmentGrantBoundary grantBoundary,
            RuntimeClosedLoopOrchestrator orchestrator,
            MediaTaskPublicationSettings publicationSettings) {
        this(boundGraphInputStore, grantBoundary, orchestrator, publicationSettings, null);
    }

    public MediaTaskActivity(
            BoundGraphInputStore boundGraphInputStore,
            AtomicAssignmentGrantBoundary grantBoundary,
            RuntimeClosedLoopOrchestrator orchestrator,
            MediaTaskPublicationSettings publicationSettings,
            FirstRenderGrantMinter grantMinter) {
        this(boundGraphInputStore, grantBoundary, orchestrator, publicationSettings, grantMinter, null);
    }

    public MediaTaskActivity(
            BoundGraphInputStore boundGraphInputStore,
            AtomicAssignmentGrantBoundary grantBoundary,
            RuntimeClosedLoopOrchestrator orchestrator,
            MediaTaskPublicationSettings publicationSettings,
            FirstRenderGrantMinter grantMinter,
            ProviderPluginCatalog providerPluginCatalog) {
        this.boundGraphInputStore = Objects.requireNonNull(
                boundGraphInputStore, "boundGraphInputStore");
        this.grantBoundary = Objects.requireNonNull(grantBoundary, "grantBoundary");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.publicationSettings = Objects.requireNonNull(
                publicationSettings, "publicationSettings");
        this.grantMinter = grantMinter;
        this.providerPluginCatalog = providerPluginCatalog;
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
        ProviderBoundExecutableTaskGraph graph = prepared.executableTaskGraph();
        Set<ExecutableTaskId> allTasks = graph.tasks().stream()
                .map(ExecutableTask::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return executePreparedGraph(
                tenantId, prepared, allTasks, Map.of(), currentGrants, durableOutputTargets,
                artifactCommitMetadata, orchestrator);
    }

    /**
     * Executes only the requested tasks of a prepared graph. Previously committed outputs may be
     * supplied as seed pins so a dependent task can be executed in a later capacity batch; the
     * orchestrator still derives and validates the exact input bindings.
     */
    public RuntimeClosedLoopResult executePreparedGraph(
            String tenantId,
            PreparedTask prepared,
            Set<ExecutableTaskId> requestedTasks,
            Map<ExecutableTaskId, ArtifactPin> seedOutputPins,
            Map<ExecutableTaskId, AssignmentGrant> currentGrants,
            Map<ExecutableTaskId, DurableOutputTarget> durableOutputTargets,
            Map<ExecutableTaskId, ArtifactCommitMetadata> artifactCommitMetadata,
            RuntimeClosedLoopOrchestrator orchestrator) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(orchestrator, "orchestrator");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(requestedTasks, "requestedTasks");
        Objects.requireNonNull(seedOutputPins, "seedOutputPins");
        Objects.requireNonNull(currentGrants, "currentGrants");
        Objects.requireNonNull(durableOutputTargets, "durableOutputTargets");
        Objects.requireNonNull(artifactCommitMetadata, "artifactCommitMetadata");

        ProviderBoundExecutableTaskGraph graph = prepared.executableTaskGraph();
        Map<ExecutableTaskId, TaskRuntimeExecution> taskExecutions = new LinkedHashMap<>();
        for (ExecutableTask task : graph.tasks()) {
            if (!requestedTasks.contains(task.id())) {
                continue;
            }
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
                tenantId, graph, requestedTasks, cacheability, taskExecutions, seedOutputPins));
    }

    private static <T> T requireEntry(
            Map<ExecutableTaskId, T> values, ExecutableTaskId taskId, String code, String detail) {
        T value = values.get(taskId);
        if (value == null) {
            throw new TaskRuntimeExecutionConstructionException(code, detail);
        }
        return value;
    }

    /**
     * P2-5b-2b-2a: executes the whole graph addressed by the stable reference.
     *
     * <p>Loads and re-derives the graph from the reference, resolves each task's current grant,
     * plans each task's publication intent from the worker's configured publication scope, and drives
     * one whole-graph {@link #executePreparedGraph} call. Nothing is fabricated: an absent grant, an
     * unconfigured publication scope or a missing runtime binding fails the activity closed.
     *
     * @throws TaskRuntimeExecutionConstructionException when a task has no current grant
     * @throws IllegalArgumentException when the worker's publication scope is not configured
     */
    public MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId) {
        return executeTask(prepared,tenantId,ignored->{});
    }

    public MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared,String tenantId,
            java.util.function.Consumer<com.example.platform.render.api.GraphRenderLifecycle.Output> beforeFinalExecution) {
        Objects.requireNonNull(prepared, "prepared");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!tenantId.equals(prepared.tenantId())) {
            throw new IllegalArgumentException(
                    "tenantId must match the prepared reference scope");
        }
        BoundGraphReference reference = new BoundGraphReference(
                prepared.tenantId(),
                prepared.renderJobId(),
                prepared.planRef(),
                prepared.planDigest(),
                prepared.expectedExecutableTaskGraphDigest());
        PreparedTask task = prepareTask(reference, tenantId);
        ProviderBoundExecutableTaskGraph graph = task.executableTaskGraph();
        TaskOutputPublicationContext publicationContext = prepared.execution()==null
                ? publicationSettings.contextFor(tenantId,prepared.renderJobId())
                : publicationSettings.contextFor(tenantId,prepared.renderJobId(),prepared.execution().projectId());

        ExecutableTask finalTask=prepared.execution()==null?null:com.example.platform.execution.taskgraph.GraphFinalOutput.requireRenderMaster(graph);
        long admissionAttempt = currentAdmissionAttempt();
        Map<ExecutableTaskId, ExecutableTask> tasksById = new LinkedHashMap<>();
        graph.tasks().forEach(executableTask -> tasksById.put(executableTask.id(), executableTask));
        Map<ExecutableTaskId, ArtifactPin> committedOutputs = new LinkedHashMap<>();
        com.example.platform.render.api.GraphRenderLifecycle.Output finalClaim = null;
        ArtifactPin finalPin = null;
        for (ExecutableTaskId taskId : graph.topologicalTaskOrder()) {
            ExecutableTask executableTask = tasksById.get(taskId);
            AssignmentGrant grant = currentGrantOrMint(
                    prepared.renderJobId(), executableTask.id(), graph, executableTask,
                    admissionAttempt);
            TaskOutputPublicationPlan publicationPlan =
                    TaskOutputPublicationPlanner.plan(executableTask, new TaskOutputPublicationContext(
                            publicationContext.tenantId(),publicationContext.renderJobId(),publicationContext.projectId(),publicationContext.storageProviderId(),publicationContext.region(),publicationContext.mediaType(),
                            finalTask==null?publicationContext.artifactKind():(executableTask.id().equals(finalTask.id())?com.example.platform.artifact.domain.ArtifactKind.RENDER_MASTER:com.example.platform.artifact.domain.ArtifactKind.DERIVED_MEDIA),publicationContext.evaluatedAt()),
                            grant.attempt().id().value(),grant.attempt().ownershipGeneration().value());
            if (finalTask != null && executableTask.id().equals(finalTask.id())) {
                finalClaim = new com.example.platform.render.api.GraphRenderLifecycle.Output(
                        finalTask.id().sha256Hex(),
                        grant.attempt().id().value(),
                        grant.attempt().ownershipGeneration().value(),
                        publicationPlan.artifactCommitMetadata().artifactId().value());
                beforeFinalExecution.accept(finalClaim);
            }
            RuntimeClosedLoopResult executed;
            try {
                executed = executePreparedGraph(
                        tenantId, task, Set.of(executableTask.id()), committedOutputs,
                        Map.of(executableTask.id(), grant),
                        Map.of(executableTask.id(), publicationPlan.durableOutputTarget()),
                        Map.of(executableTask.id(), publicationPlan.artifactCommitMetadata()),
                        orchestrator);
            } catch (IOException failure) {
                throw new IllegalStateException("media task execution failed", failure);
            }
            ArtifactPin pin = executed.outputArtifactPins().get(executableTask.id());
            if (pin == null) {
                throw new IllegalStateException(
                        "executed task did not publish an authoritative output pin");
            }
            committedOutputs.put(executableTask.id(), pin);
            if (finalTask != null && executableTask.id().equals(finalTask.id())) {
                finalPin = pin;
            }
        }
        if(finalTask!=null) {
            if(finalClaim == null || finalPin == null
                    || !finalPin.artifactId().value().equals(finalClaim.artifactId())) {
                throw new IllegalStateException(
                        "final output differs from current bound task publication");
            }
        }
        return new MediaTaskWorkflowResult(
                tenantId,
                prepared.renderJobId(),
                prepared.planDigest(),
                prepared.expectedExecutableTaskGraphDigest(),
                graph.tasks().stream()
                        .map(executableTask -> executableTask.id().sha256Hex())
                        .toList(),
                MediaTaskWorkflowResult.STATUS_EXECUTED,finalClaim);
    }

    /**
     * Resolves the current grant, delegating to the First Render minter exactly once when absent.
     *
     * <p>Fail-closed: a missing or incomplete authoritative context still raises
     * {@code GRANT_ABSENT}; nothing is fabricated and the existing success path is unchanged.
     */
    /**
     * Temporal attempt identity for the admission request. Production execution always has an
     * activity context; direct unit invocation without one is treated as the first attempt.
     */
    private static long currentAdmissionAttempt() {
        try {
            return Activity.getExecutionContext().getInfo().getAttempt();
        } catch (IllegalStateException unavailable) {
            return 1L;
        }
    }

    private AssignmentGrant currentGrantOrMint(
            String renderJobId,
            ExecutableTaskId taskId,
            ProviderBoundExecutableTaskGraph graph,
            ExecutableTask executableTask,
            long admissionAttempt) {
        var current = grantBoundary.findCurrentGrant(taskId);
        if (current.isPresent()) {
            if (grantMinter != null) grantMinter.requireLocalGrant(current.orElseThrow());
            return current.orElseThrow();
        }
        if (grantMinter != null) {
            FirstRenderGrantMinter.Result result = grantMinter.mint(
                    renderJobId, taskId, graph, requireContribution(executableTask),
                    admissionAttempt);
            if (result instanceof FirstRenderGrantMinter.Result.Minted minted) {
                var grant=grantBoundary.findCurrentGrant(taskId).orElseThrow(()->new TaskRuntimeExecutionConstructionException(
                        "GRANT_NO_LONGER_CURRENT","new grant lost its current ownership"));
                grantMinter.requireLocalGrant(grant);
                return grant;
            }
            if (result instanceof FirstRenderGrantMinter.Result.Unavailable unavailable) {
                throw new TaskRuntimeExecutionConstructionException(
                        "GRANT_ABSENT", "missing authoritative inputs: "
                                + String.join(",", unavailable.missingAuthoritativeInputs()));
            }
            if (result instanceof FirstRenderGrantMinter.Result.Rejected rejected) {
                String reason = rejected.reason();
                if (reason.equals("ADMISSION_REJECTED: RESERVATION_CONFLICT")
                        || reason.equals("ADMISSION_REJECTED: HOST_RESOURCE_SNAPSHOT_MISMATCH")
                        || reason.equals("READINESS_GENERATION_MISMATCH")
                        || reason.startsWith("ADMISSION_REPROBE_REQUIRED:")) {
                    throw new AdmissionCapacityRetryableException(reason);
                }
                throw new TaskRuntimeExecutionConstructionException(
                        "GRANT_MINT_REJECTED", reason);
            }
        }
        throw new TaskRuntimeExecutionConstructionException(
                "GRANT_ABSENT", "no current grant exists for this task");
    }

    /**
     * Resolves the exact provider contribution for the task's authoritative binding pin.
     *
     * <p>Fail closed: no catalog, or no contribution matching the task's {@code providerBindingPin},
     * raises rather than selecting an arbitrary contribution. Ambiguity for one binding pin is already
     * rejected at plugin-load time, so no selection policy is invented here.
     */
    private ProviderPluginContribution requireContribution(ExecutableTask executableTask) {
        if (providerPluginCatalog == null) {
            throw new TaskRuntimeExecutionConstructionException(
                    "PROVIDER_CONTRIBUTION_UNAVAILABLE",
                    "the worker has no provider plugin catalog; the task cannot be admitted");
        }
        return providerPluginCatalog.find(executableTask.providerBindingPin())
                .orElseThrow(() -> new TaskRuntimeExecutionConstructionException(
                        "PROVIDER_CONTRIBUTION_ABSENT",
                        "no catalog contribution matches the task's provider binding pin"));
    }
}
