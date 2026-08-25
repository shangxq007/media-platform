package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.DependencyPreservingReusePruner;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskDependency;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import com.example.platform.execution.taskgraph.ExecutionReuseKeyDeriver;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.execution.taskgraph.ReusePruningResult;
import com.example.platform.workerfabric.domain.ArtifactCommitEvidence;
import com.example.platform.workerfabric.domain.CompletionDecision;
import com.example.platform.workerfabric.domain.providernative.ProviderExecutionOutput;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Generic production Phase 16 closed loop over the accepted graph and Phase 15 runtime SPI. */
public final class RuntimeClosedLoopOrchestrator {

    private final ArtifactReuseResolver reuseResolver;
    private final ArtifactMaterializerPort artifactMaterializer;
    private final OutputStagingArea outputStagingArea;
    private final ArtifactOutputCommitOrchestrator outputCommitOrchestrator;
    private final FencedReuseCompletionOrchestrator completionOrchestrator;
    private final Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> runtimeBindings;
    private final Phase16RuntimeMetrics metrics;

    public RuntimeClosedLoopOrchestrator(
            ArtifactReuseResolver reuseResolver,
            ArtifactMaterializerPort artifactMaterializer,
            OutputStagingArea outputStagingArea,
            ArtifactOutputCommitOrchestrator outputCommitOrchestrator,
            FencedReuseCompletionOrchestrator completionOrchestrator,
            Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> runtimeBindings,
            Phase16RuntimeMetrics metrics) {
        this.reuseResolver = Objects.requireNonNull(reuseResolver, "reuseResolver");
        this.artifactMaterializer = Objects.requireNonNull(
                artifactMaterializer, "artifactMaterializer");
        this.outputStagingArea = Objects.requireNonNull(outputStagingArea, "outputStagingArea");
        this.outputCommitOrchestrator = Objects.requireNonNull(
                outputCommitOrchestrator, "outputCommitOrchestrator");
        this.completionOrchestrator = Objects.requireNonNull(
                completionOrchestrator, "completionOrchestrator");
        Objects.requireNonNull(runtimeBindings, "runtimeBindings");
        this.runtimeBindings = Map.copyOf(runtimeBindings);
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public RuntimeClosedLoopResult execute(RuntimeClosedLoopRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        ProviderBoundExecutableTaskGraph graph = request.graph();
        Map<ExecutableTaskId, ExecutionReuseKey> keys = ExecutionReuseKeyDeriver.derive(graph);
        Map<ExecutableTaskId, ValidatedReuseDecision> decisions = new LinkedHashMap<>();
        Set<ExecutableTaskId> validatedHitIds = new LinkedHashSet<>();
        for (ExecutableTask task : graph.tasks()) {
            ValidatedReuseDecision decision = reuseResolver.resolve(
                    request.tenantId(),
                    keys.get(task.id()),
                    request.cacheability().getOrDefault(task.id(), Cacheability.NOT_CACHEABLE));
            decisions.put(task.id(), decision);
            metrics.reuseLookup(decision.outcome());
            if (decision.outcome() == ValidatedReuseDecision.Outcome.VALIDATED_HIT) {
                validatedHitIds.add(task.id());
            } else if (decision.outcome() == ValidatedReuseDecision.Outcome.STALE
                    || decision.outcome() == ValidatedReuseDecision.Outcome.CORRUPT) {
                reuseResolver.evictInvalid(request.tenantId(), keys.get(task.id()), decision);
            }
        }

        ReusePruningResult pruning = DependencyPreservingReusePruner.prune(
                graph, request.requestedTasks(), Set.copyOf(validatedHitIds));
        Map<ExecutableTaskId, ArtifactPin> outputPins = new LinkedHashMap<>();
        for (ExecutableTaskId reusedTaskId : pruning.reusedTasks()) {
            outputPins.put(reusedTaskId, decisions.get(reusedTaskId).record()
                    .orElseThrow(() -> new IllegalStateException(
                            "only a typed validated hit may enter reuse pruning"))
                    .artifactPin());
        }

        Map<ExecutableTaskId, ExecutableTask> tasks = new HashMap<>();
        graph.tasks().forEach(task -> tasks.put(task.id(), task));
        Map<ExecutableTaskId, List<ExecutableTaskDependency>> incoming = incoming(graph);
        Map<ExecutableTaskId, RuntimeClosedLoopTaskResult> executed = new LinkedHashMap<>();
        for (ExecutableTaskId taskId : graph.topologicalTaskOrder()) {
            if (!pruning.tasksToExecute().contains(taskId)) {
                continue;
            }
            ExecutableTask task = tasks.get(taskId);
            TaskRuntimeExecution taskExecution = requireTaskExecution(request, task);
            List<MaterializedArtifact> inputs = materializeInputs(
                    request.tenantId(), task, incoming.getOrDefault(taskId, List.of()), outputPins);
            ProviderNativeRuntimeBinding<?> runtimeBinding = runtimeBindings.get(
                    task.providerBindingPin());
            if (runtimeBinding == null) {
                throw new IllegalStateException(
                        "no exact Phase 15 runtime binding for provider-bound executable task");
            }

            StagedExecutionOutput staged = stageProviderOutput(
                    runtimeBinding, task, taskExecution, inputs);
            DurableArtifactCommitResult committed;
            try {
                committed = outputCommitOrchestrator.commit(
                        staged,
                        taskExecution.durableOutputTarget(),
                        taskExecution.artifactCommitMetadata());
            } finally {
                deleteStagedBestEffort(staged);
            }
            ArtifactPin outputPin = new ArtifactPin(
                    committed.artifactCommitResult().artifact().artifactId(),
                    committed.artifactCommitResult().artifact().contentDigest());
            ReusableArtifactPublication publication = new ReusableArtifactPublication(
                    new ReusableArtifactRecord(
                            request.tenantId(),
                            keys.get(taskId),
                            outputPin,
                            taskId,
                            taskExecution.runtimeContext().platformExecutionAttemptId(),
                            taskExecution.runtimeContext().platformOwnershipGeneration(),
                            committed.artifactCommitResult().artifact().createdAt()));
            ArtifactCommitEvidence commitEvidence = new ArtifactCommitEvidence(
                    committed.artifactCommitResult().idempotencyKey(),
                    committed.artifactCommitResult().artifact().createdAt());
            FencedReuseCompletionResult fenced = completionOrchestrator.complete(
                    publication, taskExecution.completionEvidence(), commitEvidence);
            metrics.reusePublication(fenced.publicationResult());
            if (!authoritative(fenced)) {
                throw new NonAuthoritativeRuntimeCompletionException(committed, fenced);
            }
            outputPins.put(taskId, outputPin);
            executed.put(taskId, new RuntimeClosedLoopTaskResult(committed, fenced));
        }
        return new RuntimeClosedLoopResult(keys, decisions, pruning, outputPins, executed);
    }

    private List<MaterializedArtifact> materializeInputs(
            String tenantId,
            ExecutableTask task,
            List<ExecutableTaskDependency> dependencies,
            Map<ExecutableTaskId, ArtifactPin> outputPins) throws IOException {
        LinkedHashSet<ArtifactPin> pins = new LinkedHashSet<>();
        task.sourceArtifactPins().forEach(required -> pins.add(new ArtifactPin(
                required.artifactId(), required.contentDigest())));
        dependencies.stream()
                .sorted(Comparator.comparing(ExecutableTaskDependency::producerTaskId))
                .forEach(dependency -> pins.add(Objects.requireNonNull(
                        outputPins.get(dependency.producerTaskId()),
                        "dependency output Artifact pin is unavailable")));
        List<MaterializedArtifact> materialized = new ArrayList<>(pins.size());
        for (ArtifactPin pin : pins) {
            try {
                materialized.add(artifactMaterializer.materialize(tenantId, pin));
                metrics.materialization(Phase16RuntimeMetrics.OperationOutcome.SUCCESS);
            } catch (IOException | RuntimeException failure) {
                metrics.materialization(Phase16RuntimeMetrics.OperationOutcome.FAILURE);
                throw failure;
            }
        }
        return List.copyOf(materialized);
    }

    private StagedExecutionOutput stageProviderOutput(
            ProviderNativeRuntimeBinding<?> runtimeBinding,
            ExecutableTask task,
            TaskRuntimeExecution execution,
            List<MaterializedArtifact> inputs) throws IOException {
        ProviderExecutionOutput providerOutput = runtimeBinding.execute(
                task, execution.runtimeContext(), inputs);
        try (providerOutput) {
            try {
                StagedExecutionOutput staged = outputStagingArea.stage(providerOutput.content());
                metrics.staging(Phase16RuntimeMetrics.OperationOutcome.SUCCESS);
                return staged;
            } catch (IOException | RuntimeException failure) {
                metrics.staging(Phase16RuntimeMetrics.OperationOutcome.FAILURE);
                throw failure;
            }
        }
    }

    private static TaskRuntimeExecution requireTaskExecution(
            RuntimeClosedLoopRequest request,
            ExecutableTask task) {
        TaskRuntimeExecution execution = request.taskExecutions().get(task.id());
        if (execution == null
                || !execution.runtimeContext().executableTaskId().equals(task.id())
                || !execution.runtimeContext().providerBindingPin().equals(task.providerBindingPin())
                || !execution.artifactCommitMetadata().tenantId().equals(request.tenantId())) {
            throw new IllegalArgumentException(
                    "task execution metadata must match tenant, task, and exact provider binding");
        }
        return execution;
    }

    private static boolean authoritative(FencedReuseCompletionResult result) {
        return (result.completionDecision() == CompletionDecision.COMPLETED
                        || result.completionDecision() == CompletionDecision.DUPLICATE_NOOP)
                && (result.publicationResult() == ReusePublicationResult.ACTIVATED_WINNER
                        || result.publicationResult() == ReusePublicationResult.WINNER_IDEMPOTENT);
    }

    private static void deleteStagedBestEffort(StagedExecutionOutput staged) {
        try {
            Files.deleteIfExists(staged.path());
        } catch (IOException ignored) {
            // Durable/orphan/fencing evidence must never be masked by local staging cleanup.
        }
    }

    private static Map<ExecutableTaskId, List<ExecutableTaskDependency>> incoming(
            ProviderBoundExecutableTaskGraph graph) {
        Map<ExecutableTaskId, List<ExecutableTaskDependency>> result = new HashMap<>();
        for (ExecutableTaskDependency dependency : graph.taskDependencies()) {
            result.computeIfAbsent(dependency.consumerTaskId(), ignored -> new ArrayList<>())
                    .add(dependency);
        }
        return result;
    }
}
