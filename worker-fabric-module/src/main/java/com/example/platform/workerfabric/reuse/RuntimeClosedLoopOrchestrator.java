package com.example.platform.workerfabric.reuse;

import com.example.platform.execution.taskgraph.Cacheability;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.DependencyPreservingReusePruner;
import com.example.platform.execution.taskgraph.ExecutableInputProjection;
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
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import com.example.platform.artifact.domain.Artifact;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generic production Phase 16 closed loop over the accepted graph and Phase 15 runtime SPI. */
public final class RuntimeClosedLoopOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeClosedLoopOrchestrator.class);

    private final ArtifactReuseResolver reuseResolver;
    private final ArtifactMaterializerPort artifactMaterializer;
    private final OutputStagingArea outputStagingArea;
    private final ArtifactOutputCommitOrchestrator outputCommitOrchestrator;
    private final FencedReuseCompletionOrchestrator completionOrchestrator;
    private final Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> runtimeBindings;
    private final Phase16RuntimeMetrics metrics;
    private final RuntimePublicationTransaction nativePublicationTransaction;

    public RuntimeClosedLoopOrchestrator(
            ArtifactReuseResolver reuseResolver,
            ArtifactMaterializerPort artifactMaterializer,
            OutputStagingArea outputStagingArea,
            ArtifactOutputCommitOrchestrator outputCommitOrchestrator,
            FencedReuseCompletionOrchestrator completionOrchestrator,
            Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> runtimeBindings,
            Phase16RuntimeMetrics metrics, RuntimePublicationTransaction nativePublicationTransaction) {
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
        this.nativePublicationTransaction=Objects.requireNonNull(nativePublicationTransaction,"nativePublicationTransaction");
    }

    public record IncrementalInputs(com.example.platform.execution.taskgraph.IncrementalExecutionImpact impact,
            Map<ExecutableTaskId, ValidatedReuseDecision> decisions, Map<ExecutableTaskId, ArtifactPin> pins) {
        public IncrementalInputs { decisions=Map.copyOf(decisions); pins=Map.copyOf(pins); }
    }

    /** Reference graph proves unchanged computation; the index supplies authorized same-project historical INPUTS.
     * Existing exact current-output checks in execute remain unchanged, and final output is never admitted here. */
    public IncrementalInputs resolveIncrementalInputs(String tenant, String project,
            ProviderBoundExecutableTaskGraph target, ProviderBoundExecutableTaskGraph reference) {
        var impact = com.example.platform.execution.taskgraph.IncrementalExecutionImpact.analyze(target, reference);
        var cacheability = com.example.platform.execution.taskgraph.TaskCacheabilityDeriver.derive(target);
        Map<ExecutableTaskId, ValidatedReuseDecision> decisions = new LinkedHashMap<>();
        Map<ExecutableTaskId, ArtifactPin> pins = new LinkedHashMap<>();
        for (var task : target.tasks()) {
            var change = impact.decisions().get(task.id());
            var decision = change.unchanged()
                    ? reuseResolver.resolveIntermediate(tenant, project, change.key(), cacheability.get(task.id()), task.id())
                    : ValidatedReuseDecision.reject(ValidatedReuseDecision.Outcome.MISS, change.reason());
            decisions.put(task.id(), decision);
            if (decision.outcome() == ValidatedReuseDecision.Outcome.VALIDATED_HIT)
                pins.put(task.id(), decision.record().orElseThrow().artifactPin());
        }
        return new IncrementalInputs(impact, decisions, pins);
    }

    public RuntimeClosedLoopResult execute(RuntimeClosedLoopRequest request) throws IOException {
        return execute(request,(task,execution,action)->action.commit(),null);
    }
    public RuntimeClosedLoopResult execute(RuntimeClosedLoopRequest request,
            RuntimePublicationTransaction publicationTransaction,
            com.example.platform.sandbox.SandboxCancellation cancellation) throws IOException {
        Objects.requireNonNull(publicationTransaction,"publicationTransaction");
        Objects.requireNonNull(request, "request");
        ProviderBoundExecutableTaskGraph graph = request.graph();
        validateAuthoritativeOutputCardinality(graph);
        Map<ExecutableTaskId, ExecutionReuseKey> keys = ExecutionReuseKeyDeriver.derive(graph);
        Map<ExecutableTaskId, ValidatedReuseDecision> decisions = new LinkedHashMap<>();
        Set<ExecutableTaskId> validatedHitIds = new LinkedHashSet<>();
        for (ExecutableTask task : graph.tasks()) {
            ValidatedReuseDecision decision = reuseResolver.resolve(
                    request.tenantId(),
                    keys.get(task.id()),
                    request.cacheability().getOrDefault(task.id(), Cacheability.NOT_CACHEABLE));
            // A reuse hint is not permission to substitute another job/attempt's immutable publication.
            var target=request.taskExecutions().get(task.id());
            if(decision.outcome()==ValidatedReuseDecision.Outcome.VALIDATED_HIT && target!=null && target.artifactCommitMetadata().projectId()!=null
                    && !target.artifactCommitMetadata().artifactId().equals(decision.record().orElseThrow().artifactPin().artifactId())) {
                decision=ValidatedReuseDecision.reject(ValidatedReuseDecision.Outcome.MISS,
                        "reuse publication does not match this execution's exact output identity");
            }
            decisions.put(task.id(), decision);
            metrics.reuseLookup(decision.outcome());
            if (decision.outcome() == ValidatedReuseDecision.Outcome.VALIDATED_HIT) {
                validatedHitIds.add(task.id());
            } else if (decision.outcome() == ValidatedReuseDecision.Outcome.STALE
                    || decision.outcome() == ValidatedReuseDecision.Outcome.CORRUPT) {
                reuseResolver.evictInvalid(request.tenantId(), keys.get(task.id()), decision);
            }
        }

        validatedHitIds.addAll(request.seedOutputPins().keySet());
        ReusePruningResult pruning = DependencyPreservingReusePruner.prune(
                graph, request.requestedTasks(), Set.copyOf(validatedHitIds));
        Map<ExecutableTaskId, ArtifactPin> outputPins =
                new LinkedHashMap<>(request.seedOutputPins());
        for (ExecutableTaskId reusedTaskId : pruning.reusedTasks()) {
            if (request.seedOutputPins().containsKey(reusedTaskId)) {
                continue;
            }
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
            List<MaterializedExecutionInput> inputs = materializeInputs(
                    request.tenantId(), taskExecution.artifactCommitMetadata().projectId(), task,
                    incoming.getOrDefault(taskId, List.of()), outputPins);
            String attemptId = taskExecution.runtimeContext().platformExecutionAttemptId().value();
            String renderJobId = taskExecution.artifactCommitMetadata().renderJobId();
            for (MaterializedExecutionInput input : inputs) {
                LOG.info("BYTE_INPUT job={} task={} attempt={} input={} artifact={} pinDigest={} bytes={} fileDigest={}",
                        renderJobId, taskId.sha256Hex(), attemptId, input.inputId().value(),
                        input.artifactPin().artifactId().value(),
                        input.artifactPin().contentDigest().canonicalValue(),
                        input.materializedArtifact().byteLength(),
                        fileDigest(input.materializedArtifact().path()));
            }
            ProviderNativeRuntimeBinding<?> runtimeBinding = runtimeBindings.get(
                    task.providerBindingPin());
            if (runtimeBinding == null) {
                throw new IllegalStateException(
                        "no exact Phase 15 runtime binding for provider-bound executable task");
            }

            StagedExecutionOutput staged = stageProviderOutput(
                    cancellation == null ? runtimeBinding : runtimeBinding.withCancellation(cancellation), task, taskExecution, inputs);
            LOG.info("BYTE_STAGED job={} task={} attempt={} bytes={} sha256={} path={}",
                    renderJobId, taskId.sha256Hex(), attemptId, staged.byteLength(),
                    staged.contentDigest().canonicalValue(), staged.path());
            RuntimeClosedLoopTaskResult published;
            try {
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Runtime output cancelled before commitment");
                published=publicationTransaction.commit(taskId,taskExecution,
                        ()->nativePublicationTransaction.commit(taskId,taskExecution,()-> {
                    var metadata=taskExecution.artifactCommitMetadata();
                    if(!taskExecution.derivations().isEmpty()) {
                        var provenance=new ArrayList<>(metadata.provenanceDeclarations());
                        taskExecution.derivations().forEach(intent->provenance.add(intent.declaration(
                                taskExecution.runtimeContext().platformExecutionAttemptId().value(),staged.contentDigest())));
                        metadata=new ArtifactCommitMetadata(metadata.artifactId(),metadata.tenantId(),metadata.mediaType(),
                                metadata.artifactKind(),metadata.schemaVersion(),metadata.replicaRole(),metadata.region(),provenance,
                                metadata.evaluatedAt(),metadata.createdAt(),metadata.renderJobId(),metadata.projectId());
                    }
                    DurableArtifactCommitResult committed=outputCommitOrchestrator.commit(staged,taskExecution.durableOutputTarget(),metadata);
                    ArtifactPin outputPin=new ArtifactPin(committed.artifactCommitResult().artifact().artifactId(),
                            committed.artifactCommitResult().artifact().contentDigest());
                    var publication=new ReusableArtifactPublication(new ReusableArtifactRecord(request.tenantId(),keys.get(taskId),
                            outputPin,taskId,taskExecution.runtimeContext().platformExecutionAttemptId(),
                            taskExecution.runtimeContext().platformOwnershipGeneration(),committed.artifactCommitResult().artifact().createdAt()));
                    var commitEvidence=new ArtifactCommitEvidence(committed.artifactCommitResult().artifact().artifactId());
                    var fenced=completionOrchestrator.complete(publication,taskExecution.completionEvidence(),commitEvidence);
                    metrics.reusePublication(fenced.publicationResult());
                    if(!authoritative(fenced))throw new NonAuthoritativeRuntimeCompletionException(committed,fenced);
                    return new RuntimeClosedLoopTaskResult(committed,fenced);
                }));
            } finally {
                deleteStagedBestEffort(staged);
            }
            var artifact=published.durableArtifactCommit().artifactCommitResult().artifact();
            LOG.info("BYTE_COMMITTED job={} task={} attempt={} artifact={} bytes={} sha256={}",
                    renderJobId, taskId.sha256Hex(), attemptId, artifact.artifactId().value(),
                    artifact.byteLength(), artifact.contentDigest().canonicalValue());
            outputPins.put(taskId,new ArtifactPin(artifact.artifactId(),artifact.contentDigest()));
            executed.put(taskId,published);
        }
        return new RuntimeClosedLoopResult(keys, decisions, pruning, outputPins, executed);
    }

    private List<MaterializedExecutionInput> materializeInputs(
            String tenantId,
            String projectId,
            ExecutableTask task,
            List<ExecutableTaskDependency> dependencies,
            Map<ExecutableTaskId, ArtifactPin> outputPins) throws IOException {
        Map<ExecutionInputId, ExecutableInputProjection> knownInputs = new HashMap<>();
        task.requiredRuntimeInputs().stream()
                .forEach(input -> {
                    if (knownInputs.putIfAbsent(input.inputId(), input) != null) {
                        throw new IllegalArgumentException(
                                "executable task contains duplicate logical input identity");
                    }
                });

        List<ResolvedExecutionInput> resolved = new ArrayList<>();
        task.sourceArtifactPins().forEach(source -> resolved.add(new ResolvedExecutionInput(
                source.inputId(),
                new ArtifactPin(source.artifactId(), source.contentDigest()))));
        dependencies.stream()
                .sorted(Comparator.comparing(
                        dependency -> dependency.consumerInput().inputId().value()))
                .forEach(dependency -> {
                    if (!dependency.consumerTaskId().equals(task.id())
                            || !knownInputs.containsKey(dependency.consumerInput().inputId())) {
                        throw new IllegalArgumentException(
                                "computed runtime input is unknown to the exact consumer task");
                    }
                    ExecutableInputProjection consumerInput =
                            task.requireExactRuntimeInput(dependency.consumerInput());
                    ArtifactPin outputPin = outputPins.get(dependency.producerTaskId());
                    if (outputPin == null) {
                        throw new IllegalStateException(
                                "dependency output Artifact pin is unavailable");
                    }
                    resolved.add(new ResolvedExecutionInput(
                            consumerInput.inputId(), outputPin));
                });
        resolved.sort(Comparator.comparing(value -> value.inputId().value()));

        Set<ExecutionInputId> resolvedInputIds = new LinkedHashSet<>();
        for (ResolvedExecutionInput input : resolved) {
            if (!knownInputs.containsKey(input.inputId())) {
                throw new IllegalArgumentException("unknown logical runtime input identity");
            }
            if (!resolvedInputIds.add(input.inputId())) {
                throw new IllegalArgumentException("duplicate logical runtime input identity");
            }
        }
        if (!resolvedInputIds.equals(knownInputs.keySet())) {
            throw new IllegalArgumentException("required logical runtime input identity is absent");
        }

        List<MaterializedExecutionInput> materialized = new ArrayList<>(resolved.size());
        for (ResolvedExecutionInput input : resolved) {
            try {
                ArtifactMaterializationResult result = artifactMaterializer.materialize(
                        tenantId, projectId, input.artifactPin());
                materialized.add(new MaterializedExecutionInput(
                        input.inputId(), input.artifactPin(), result.materializedArtifact()));
                metrics.materialization(result.disposition());
            } catch (IOException | RuntimeException failure) {
                metrics.materialization(MaterializationDisposition.FAILURE);
                throw failure;
            }
        }
        return List.copyOf(materialized);
    }

    private StagedExecutionOutput stageProviderOutput(
            ProviderNativeRuntimeBinding<?> runtimeBinding,
            ExecutableTask task,
            TaskRuntimeExecution execution,
            List<MaterializedExecutionInput> inputs) throws IOException {
        ProviderExecutionOutput providerOutput = runtimeBinding.execute(
                task, execution.runtimeContext(), inputs);
        StagedExecutionOutput staged=null;boolean handedOff=false;
        try {
            try(providerOutput) {staged=outputStagingArea.stage(providerOutput.content());}
            metrics.staging(Phase16RuntimeMetrics.OperationOutcome.SUCCESS);handedOff=true;return staged;
        } finally {
            if(!handedOff) {
                if(staged!=null)deleteStagedBestEffort(staged);
                metrics.staging(Phase16RuntimeMetrics.OperationOutcome.FAILURE);
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
                        || result.publicationResult() == ReusePublicationResult.WINNER_IDEMPOTENT
                        || result.publicationResult() == ReusePublicationResult.COMPLETED_WITHOUT_CACHE);
    }

    private static void validateAuthoritativeOutputCardinality(
            ProviderBoundExecutableTaskGraph graph) {
        for (ExecutableTask task : graph.tasks()) {
            int outputCount = task.authoritativeOutputIds().size();
            if (outputCount != 1) {
                throw new ProviderNativeExecutionFailure(
                        ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY,
                        "Phase 16 V1 requires exactly one platform-authoritative output",
                        Map.of(
                                "authoritativeOutputCount", Integer.toString(outputCount),
                                "executableTaskId", task.id().sha256Hex()));
            }
        }
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

    private record ResolvedExecutionInput(
            ExecutionInputId inputId,
            ArtifactPin artifactPin) {

        private ResolvedExecutionInput {
            Objects.requireNonNull(inputId, "inputId");
            Objects.requireNonNull(artifactPin, "artifactPin");
        }
    }

    private static String fileDigest(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

}
