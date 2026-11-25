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

/** Worker orchestration over verified bound-graph inputs and existing Execution/Artifact contracts.
 * The Temporal adapter owns calls into Render's public lifecycle. This component does not write
 * Render state, bypass native fencing, choose an arbitrary final row or create another planner. */
public final class MediaTaskActivity {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(MediaTaskActivity.class);

    private final BoundGraphInputStore boundGraphInputStore;
    private final AtomicAssignmentGrantBoundary grantBoundary;
    private final RuntimeClosedLoopOrchestrator orchestrator;
    private final MediaTaskPublicationSettings publicationSettings;
    private final MediaTaskGrantMinter grantMinter;
    private final ProviderPluginCatalog providerPluginCatalog;
    private final com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary nativeLifecycle;

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
            MediaTaskGrantMinter grantMinter) {
        this(boundGraphInputStore, grantBoundary, orchestrator, publicationSettings, grantMinter, null);
    }

    public MediaTaskActivity(
            BoundGraphInputStore boundGraphInputStore,
            AtomicAssignmentGrantBoundary grantBoundary,
            RuntimeClosedLoopOrchestrator orchestrator,
            MediaTaskPublicationSettings publicationSettings,
            MediaTaskGrantMinter grantMinter,
            ProviderPluginCatalog providerPluginCatalog) {
        this(boundGraphInputStore, grantBoundary, orchestrator, publicationSettings, grantMinter, providerPluginCatalog, null);
    }

    public MediaTaskActivity(BoundGraphInputStore boundGraphInputStore, AtomicAssignmentGrantBoundary grantBoundary,
            RuntimeClosedLoopOrchestrator orchestrator, MediaTaskPublicationSettings publicationSettings,
            MediaTaskGrantMinter grantMinter, ProviderPluginCatalog providerPluginCatalog,
            com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary nativeLifecycle) {
        this.nativeLifecycle = nativeLifecycle;
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
        return executePreparedGraph(tenantId, prepared, requestedTasks, seedOutputPins, currentGrants, durableOutputTargets,
                artifactCommitMetadata, orchestrator, (id, execution, action) -> action.commit(), null);
    }

    private RuntimeClosedLoopResult executePreparedGraph(String tenantId, PreparedTask prepared,
            Set<ExecutableTaskId> requestedTasks, Map<ExecutableTaskId, ArtifactPin> seedOutputPins,
            Map<ExecutableTaskId, AssignmentGrant> currentGrants, Map<ExecutableTaskId, DurableOutputTarget> durableOutputTargets,
            Map<ExecutableTaskId, ArtifactCommitMetadata> artifactCommitMetadata, RuntimeClosedLoopOrchestrator orchestrator,
            com.example.platform.workerfabric.reuse.RuntimePublicationTransaction publication,
            com.example.platform.sandbox.SandboxCancellation cancellation) throws IOException {
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
                tenantId, graph, requestedTasks, cacheability, taskExecutions, seedOutputPins), publication, cancellation);
    }

    public RuntimeClosedLoopOrchestrator.IncrementalInputs incrementalInputs(PreparedTaskRef prepared, String tenant,
            java.util.Optional<com.example.platform.render.api.GraphRenderLifecycle.IncrementalReference> reference) {
        if (reference.isEmpty()) return null;
        try {
            var previous=boundGraphInputStore.findReference(tenant, reference.orElseThrow().jobId());
            if(previous.isEmpty()) { LOG.info("INCREMENTAL_FALLBACK job={} reason=REFERENCE_GRAPH_UNAVAILABLE",prepared.renderJobId()); return null; }
            var current=prepareTask(new BoundGraphReference(tenant,prepared.renderJobId(),prepared.planRef(),prepared.planDigest(),prepared.expectedExecutableTaskGraphDigest()),tenant);
            var old=prepareTask(previous.orElseThrow(),tenant);
            var result=orchestrator.resolveIncrementalInputs(tenant,prepared.execution().projectId(),current.executableTaskGraph(),old.executableTaskGraph());
            LOG.info("INCREMENTAL_IMPACT job={} referenceJob={} affectedRegions={} reusableInputs={}",prepared.renderJobId(),reference.orElseThrow().jobId(),result.impact().affectedRegions(),result.pins().size());
            return result;
        } catch (RuntimeException unavailable) {
            LOG.warn("INCREMENTAL_FALLBACK job={} reason={}",prepared.renderJobId(),unavailable.getClass().getSimpleName());
            return null;
        }
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
            java.util.function.Consumer<com.example.platform.render.api.GraphRenderLifecycle.Output> bindFinalPublication) {
        return executeTask(prepared, tenantId, bindFinalPublication, (id, execution, action) -> action.commit(), null);
    }

    public MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId,
            java.util.function.Consumer<com.example.platform.render.api.GraphRenderLifecycle.Output> bindFinalPublication,
            com.example.platform.workerfabric.reuse.RuntimePublicationTransaction publication,
            com.example.platform.sandbox.SandboxCancellation cancellation) {
        return executeTask(prepared,tenantId,bindFinalPublication,publication,cancellation,null);
    }
    public MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId,
            java.util.function.Consumer<com.example.platform.render.api.GraphRenderLifecycle.Output> bindFinalPublication,
            com.example.platform.workerfabric.reuse.RuntimePublicationTransaction publication,
            com.example.platform.sandbox.SandboxCancellation cancellation,
            RuntimeClosedLoopOrchestrator.IncrementalInputs incremental) {
        return executeTask(prepared, tenantId, bindFinalPublication, publication, cancellation, incremental, Map.of());
    }

    MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId,
            java.util.function.Consumer<com.example.platform.render.api.GraphRenderLifecycle.Output> bindFinalPublication,
            com.example.platform.workerfabric.reuse.RuntimePublicationTransaction publication,
            com.example.platform.sandbox.SandboxCancellation cancellation,
            RuntimeClosedLoopOrchestrator.IncrementalInputs incremental,
            Map<ExecutableTaskId, ArtifactPin> acceptedForThisJob) {
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
        var keys=com.example.platform.execution.taskgraph.ExecutionReuseKeyDeriver.derive(graph);
        var cacheability=com.example.platform.execution.taskgraph.TaskCacheabilityDeriver.derive(graph);
        com.example.platform.render.api.GraphRenderLifecycle.Output finalClaim = null;
        ArtifactPin finalPin = null;
        var order = new java.util.ArrayList<>(graph.topologicalTaskOrder());
        if (finalTask != null) {
            if (graph.taskDependencies().stream().anyMatch(edge -> edge.producerTaskId().equals(finalTask.id()))) {
                throw new IllegalArgumentException("The declared render master must be terminal");
            }
            // A bound and committed master can then recover the whole job without historical source reads.
            order.remove(finalTask.id());
            order.add(finalTask.id());
        }
        for (ExecutableTaskId taskId : order) {
            if (cancellation != null && cancellation.isCancellationRequested())
                throw new IllegalStateException("Render execution cancelled before task admission");
            ExecutableTask executableTask = tasksById.get(taskId);
            if (acceptedForThisJob.containsKey(taskId)) {
                if (finalTask != null && finalTask.id().equals(taskId)) {
                    throw new IllegalStateException("A partially completed graph cannot recover its master before its other outputs");
                }
                committedOutputs.put(taskId, acceptedForThisJob.get(taskId));
                LOG.info("RECOVERED_TASK_OUTPUT job={} task={} artifact={}", prepared.renderJobId(),
                        taskId.sha256Hex(), acceptedForThisJob.get(taskId).artifactId().value());
                continue;
            }
            if (incremental != null && incremental.pins().containsKey(taskId)) {
                if (finalTask == null || finalTask.id().equals(taskId)) throw new IllegalStateException("Final output cannot be borrowed from a reference Job");
                var input=incremental.pins().get(taskId);
                committedOutputs.put(taskId,input);
                traceTask(prepared,graph,executableTask,keys.get(taskId),cacheability.get(taskId),incremental,false,input,committedOutputs);
                continue;
            }
            AssignmentGrant grant = currentGrantOrMint(
                    prepared.renderJobId(), executableTask.id(), graph, executableTask,
                    admissionAttempt);
            var physicallyFinished = new java.util.concurrent.atomic.AtomicBoolean();
            try {
            TaskOutputPublicationPlan publicationPlan =
                    TaskOutputPublicationPlanner.plan(executableTask, new TaskOutputPublicationContext(
                            publicationContext.tenantId(),publicationContext.renderJobId(),publicationContext.projectId(),publicationContext.storageProviderId(),publicationContext.region(),publicationContext.mediaType(),
                            finalTask == null ? publicationContext.artifactKind() : TaskOutputPublicationPlanner.renderArtifactKind(executableTask),publicationContext.evaluatedAt()),
                            grant.attempt().id().value(),grant.attempt().ownershipGeneration().value());
            if (finalTask != null && executableTask.id().equals(finalTask.id())) {
                finalClaim = new com.example.platform.render.api.GraphRenderLifecycle.Output(
                        finalTask.id().sha256Hex(),
                        grant.attempt().id().value(),
                        grant.attempt().ownershipGeneration().value(),
                        publicationPlan.artifactCommitMetadata().artifactId().value());
            }
            var outputClaim = finalTask != null && executableTask.id().equals(finalTask.id()) ? finalClaim : null;
            RuntimeClosedLoopResult executed;
            try {
                executed = executePreparedGraph(
                        tenantId, task, Set.of(executableTask.id()), committedOutputs,
                        Map.of(executableTask.id(), grant),
                        Map.of(executableTask.id(), publicationPlan.durableOutputTarget()),
                        Map.of(executableTask.id(), publicationPlan.artifactCommitMetadata()),
                        orchestrator, (id, execution, action) -> {
                            physicallyFinished.set(true); // provider returned staged bytes; its process has finished
                            return publication.commit(id, execution, () -> {
                                var result = action.commit();
                                if (outputClaim != null) bindFinalPublication.accept(outputClaim);
                                return result;
                            });
                        }, cancellation);
            } catch (IOException failure) {
                throw new IllegalStateException("media task execution failed", failure);
            }
            ArtifactPin pin = executed.outputArtifactPins().get(executableTask.id());
            if (pin == null) {
                throw new IllegalStateException(
                        "executed task did not publish an authoritative output pin");
            }
            traceTask(prepared,graph,executableTask,keys.get(taskId),cacheability.get(taskId),incremental,
                    executed.executedTaskResults().containsKey(taskId),pin,committedOutputs);
            committedOutputs.put(executableTask.id(), pin);
            if (finalTask != null && executableTask.id().equals(finalTask.id())) {
                finalPin = pin;
            }
            release(grant, true);
            } catch (RuntimeException failure) {
                var nativeFailure = failure instanceof com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure f ? f : null;
                boolean knownExit = physicallyFinished.get() || (nativeFailure != null
                        && nativeFailure.code() != com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode.RUNTIME_EXECUTION_UNKNOWN
                        && nativeFailure.code() != com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode.PROCESS_CLEANUP_FAILED);
                try { release(grant, knownExit); }
                catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
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
     * Resolves the current grant, delegating to the MediaTask minter exactly once when absent.
     *
     * <p>Fail-closed: a missing or incomplete authoritative context still raises
     * {@code GRANT_ABSENT}; nothing is fabricated and the existing success path is unchanged.
     */
    /**
     * Temporal attempt identity for the admission request. Production execution always has an
     * activity context; direct unit invocation without one is treated as the first attempt.
     */
    private static void traceTask(PreparedTaskRef prepared, ProviderBoundExecutableTaskGraph graph, ExecutableTask task,
            com.example.platform.execution.taskgraph.ExecutionReuseKey key, Cacheability cacheability,
            RuntimeClosedLoopOrchestrator.IncrementalInputs incremental, boolean executed, ArtifactPin output,
            Map<ExecutableTaskId,ArtifactPin> inputs) {
        Map<String,Object> event=new LinkedHashMap<>();
        event.put("job",prepared.renderJobId());event.put("task",task.id().sha256Hex());
        event.put("provider",task.providerBindingPin().providerId().value());
        event.put("implementation",task.providerBindingPin().providerImplementationId().value());
        event.put("profile",task.providerBindingPin().providerCapabilityProfileVersionOrDigest());
        event.put("graphDigest",graph.digest().sha256Hex());
        event.put("cacheability",cacheability.name());event.put("reuseKeyVersion",key.version());event.put("reuseKey",key.stableDigest());
        event.put("decision",incremental==null?"FULL_RENDER":incremental.impact().decisions().get(task.id()).reason());
        event.put("resolver",incremental==null?"EXACT_OUTPUT_POLICY":incremental.decisions().get(task.id()).outcome().name());
        event.put("effective",executed?"EXECUTED":"SKIPPED_AUTHORIZED_INPUT");event.put("executed",executed);
        event.put("outputArtifact",output.artifactId().value());event.put("outputDigest",output.contentDigest().canonicalValue());
        event.put("sourceArtifacts",task.sourceArtifactPins().stream().map(p->p.artifactId().value()).toList());
        var consumed = new java.util.ArrayList<String>();
        task.sourceArtifactPins().forEach(p->consumed.add(p.artifactId().value()));
        graph.taskDependencies().stream().filter(d->d.consumerTaskId().equals(task.id()))
                .map(d->inputs.get(d.producerTaskId())).filter(Objects::nonNull).forEach(p->consumed.add(p.artifactId().value()));
        event.put("inputArtifacts",consumed.stream().sorted().toList());
        event.put("providerBinding",task.providerBindingPin().toString());
        try {LOG.info("RENDER_TASK_DECISION {}",new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event));}
        catch(com.fasterxml.jackson.core.JsonProcessingException unavailable) {LOG.warn("RENDER_TASK_DECISION_SERIALIZATION_FAILED job={} task={}",prepared.renderJobId(),task.id());}
    }

    private void release(AssignmentGrant grant, boolean physicalExitConfirmed) {
        if (nativeLifecycle == null) return; // direct planning fixtures do not own an executing native runtime
        var fence = com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.LeaseOwnershipFence.from(grant.lease());
        nativeLifecycle.cancelAttempt(new com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.AttemptCancellation(fence, java.time.Instant.now()));
        if (physicalExitConfirmed) nativeLifecycle.confirmPhysicalRelease(
                new com.example.platform.workerfabric.domain.ExecutionLifecycleBoundary.PhysicalReleaseConfirmation(
                        "first-render-release:" + grant.attempt().id().value(), fence, grant.assignment().reservationIds(), java.time.Instant.now()));
    }

    private static long currentAdmissionAttempt() {
        try {
            return Activity.getExecutionContext().getInfo().getAttempt();
        } catch (IllegalStateException unavailable) {
            return 1L;
        }
    }

    static void requireOwnedGrant(AssignmentGrant grant, String job, ExecutableTaskId task, long activityAttempt) {
        if (!MediaTaskGrantMinter.requestWorkId(job, task, activityAttempt).equals(grant.requestWorkId()))
            throw new AdmissionCapacityRetryableException("TASK_OWNERSHIP_BUSY");
    }

    private AssignmentGrant currentGrantOrMint(
            String renderJobId,
            ExecutableTaskId taskId,
            ProviderBoundExecutableTaskGraph graph,
            ExecutableTask executableTask,
            long admissionAttempt) {
        var current = grantBoundary.findCurrentGrant(taskId);
        if (current.isPresent()) {
            if (grantMinter != null) {
                requireOwnedGrant(current.orElseThrow(), renderJobId, taskId, admissionAttempt);
                grantMinter.requireLocalGrant(current.orElseThrow());
            }
            return current.orElseThrow();
        }
        if (grantMinter != null) {
            MediaTaskGrantMinter.Result result = grantMinter.mint(
                    renderJobId, taskId, graph, requireContribution(executableTask),
                    admissionAttempt);
            if (result instanceof MediaTaskGrantMinter.Result.Minted minted) {
                var grant=grantBoundary.findCurrentGrant(taskId).orElseThrow(()->new TaskRuntimeExecutionConstructionException(
                        "GRANT_NO_LONGER_CURRENT","new grant lost its current ownership"));
                requireOwnedGrant(grant, renderJobId, taskId, admissionAttempt);
                grantMinter.requireLocalGrant(grant);
                return grant;
            }
            if (result instanceof MediaTaskGrantMinter.Result.Unavailable unavailable) {
                throw new TaskRuntimeExecutionConstructionException(
                        "GRANT_ABSENT", "missing authoritative inputs: "
                                + String.join(",", unavailable.missingAuthoritativeInputs()));
            }
            if (result instanceof MediaTaskGrantMinter.Result.Rejected rejected) {
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
