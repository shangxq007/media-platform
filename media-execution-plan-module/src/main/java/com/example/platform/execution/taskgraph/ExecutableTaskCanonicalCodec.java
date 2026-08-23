package com.example.platform.execution.taskgraph;

import com.example.platform.execution.composition.CompositionDecision;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.compatibility.ProviderCompatibilityTransition;
import com.example.platform.execution.domain.provider.ProviderCanonicalCodec;
import com.example.platform.execution.planning.Canonical;
import com.example.platform.execution.planning.CanonicalWriter;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Explicit versioned structural encoding shared by task identity and ETG digest. */
final class ExecutableTaskCanonicalCodec {

    private ExecutableTaskCanonicalCodec() {
    }

    static String taskSemantics(
            CompositionDecision compositionDecision,
            List<BoundaryAction> boundaryActions,
            List<ExecutableTask.RequiredInputArtifactPin> requiredInputArtifactPins) {
        List<String> memberships = compositionDecision.memberships().stream()
                .map(ExecutableTaskCanonicalCodec::membership)
                .toList();
        List<String> actions = boundaryActions.stream()
                .map(ExecutableTaskCanonicalCodec::boundaryAction)
                .toList();
        List<String> inputPins = requiredInputArtifactPins.stream()
                .map(ExecutableTaskCanonicalCodec::requiredInputArtifactPin)
                .sorted()
                .toList();
        return new CanonicalWriter()
                .tag("roadmap22.executable-task.v1")
                .field("providerBindingPin", providerBinding(compositionDecision))
                .field("compositionDecision", compositionDecision(compositionDecision))
                .field("memberships", list(memberships))
                .field("boundaryActions", list(actions))
                .field("requiredInputArtifactPins", list(inputPins))
                .build();
    }

    static String taskWithIdentity(ExecutableTask task) {
        return new CanonicalWriter()
                .tag("roadmap22.executable-task-with-identity.v1")
                .field("taskId", task.id().sha256Hex())
                .field("semantics", taskSemantics(
                        task.compositionDecision(),
                        task.boundaryActions(),
                        task.requiredInputArtifactPins()))
                .build();
    }

    static String graphSemantics(
            String graphSchemaVersion,
            String sourcePlanFormatVersion,
            int sourcePlanSchemaVersion,
            String sourcePlanFingerprint,
            List<ExecutableTask> tasks,
            List<ProviderLocalTaskDependency> internalDependencies,
            List<ExecutableTaskDependency> taskDependencies,
            List<ProviderCompatibilityTransition> selectedProviderTransitions,
            List<MandatoryArtifactBoundary> mandatoryArtifactBoundaries,
            List<ExecutionArtifactBoundary> executionArtifactBoundaries,
            List<ExecutableTask.RequiredInputArtifactPin> requiredInputArtifactPins) {
        List<String> taskCanonicals = tasks.stream()
                .map(ExecutableTaskCanonicalCodec::taskWithIdentity).sorted().toList();
        List<String> membershipStructure = tasks.stream()
                .flatMap(task -> task.memberships().stream().map(membership ->
                        new CanonicalWriter()
                                .tag("roadmap22.graph-membership.v1")
                                .field("taskId", task.id().sha256Hex())
                                .field("membership", membership(membership))
                                .build()))
                .sorted().toList();
        List<String> providerBindings = tasks.stream()
                .map(task -> new CanonicalWriter()
                        .tag("roadmap22.graph-provider-binding.v1")
                        .field("taskId", task.id().sha256Hex())
                        .field("providerBindingPin", new String(
                                ProviderCanonicalCodec.serialize(task.providerBindingPin()),
                                StandardCharsets.UTF_8))
                        .build())
                .sorted().toList();
        List<String> compositionDecisions = tasks.stream()
                .map(task -> new CanonicalWriter()
                        .tag("roadmap22.graph-composition-decision.v1")
                        .field("taskId", task.id().sha256Hex())
                        .field("decision", compositionDecision(task.compositionDecision()))
                        .build())
                .sorted().toList();
        List<String> actions = tasks.stream()
                .flatMap(task -> task.boundaryActions().stream().map(action ->
                        new CanonicalWriter()
                                .tag("roadmap22.graph-boundary-action.v1")
                                .field("primaryTaskId", task.id().sha256Hex())
                                .field("action", boundaryAction(action))
                                .build()))
                .sorted().toList();
        List<String> internals = internalDependencies.stream()
                .map(ExecutableTaskCanonicalCodec::internalDependency).sorted().toList();
        List<String> dependencies = taskDependencies.stream()
                .map(ExecutableTaskCanonicalCodec::taskDependency).sorted().toList();
        List<String> transitions = selectedProviderTransitions.stream()
                .map(ExecutableTaskCanonicalCodec::providerTransition).sorted().toList();
        List<String> boundaries = mandatoryArtifactBoundaries.stream()
                .map(ExecutableTaskCanonicalCodec::mandatoryArtifactBoundary).sorted().toList();
        List<String> executionBoundaries = executionArtifactBoundaries.stream()
                .map(ExecutableTaskCanonicalCodec::executionArtifactBoundary).sorted().toList();
        List<String> inputPins = requiredInputArtifactPins.stream()
                .map(ExecutableTaskCanonicalCodec::requiredInputArtifactPin).sorted().toList();
        return new CanonicalWriter()
                .tag("roadmap22.provider-bound-executable-task-graph.v2")
                .field("graphSchemaVersion", graphSchemaVersion)
                .field("sourcePlanFormatVersion", sourcePlanFormatVersion)
                .field("sourcePlanSchemaVersion", Integer.toString(sourcePlanSchemaVersion))
                .field("sourcePlanFingerprint", sourcePlanFingerprint)
                .field("tasks", list(taskCanonicals))
                .field("membershipStructure", list(membershipStructure))
                .field("dependencyTopology", list(dependencies))
                .field("providerLocalDependencyTopology", list(internals))
                .field("providerTransitionSemantics", list(transitions))
                .field("providerBindingSemantics", list(providerBindings))
                .field("boundaryActionSemantics", list(actions))
                .field("mandatoryMaterializationSemantics", list(boundaries))
                .field("executionArtifactBoundarySemantics", list(executionBoundaries))
                .field("requiredInputArtifactPins", list(inputPins))
                .field("providerLocalCompositionDecisions", list(compositionDecisions))
                .build();
    }

    static String membership(ExecutableTaskMembership membership) {
        return new CanonicalWriter()
                .tag("roadmap22.executable-task-membership.v1")
                .field("canonicalPosition", Integer.toString(membership.canonicalPosition()))
                .field("physicalPlanUnit", physicalPlanUnit(membership.physicalPlanUnit()))
                .build();
    }

    static String boundaryAction(BoundaryAction action) {
        return new CanonicalWriter()
                .tag("roadmap22.boundary-action.v1")
                .field("phase", action.phase().name())
                .field("deterministicOrder", Integer.toString(action.deterministicOrder()))
                .field("target", boundaryTarget(action.target()))
                .build();
    }

    static String requiredInputArtifactPin(ExecutableTask.RequiredInputArtifactPin pin) {
        return new CanonicalWriter()
                .tag("roadmap22.required-input-artifact-pin.v1")
                .field("consumerUnitId", pin.consumerUnitId().value())
                .field("inputBinding", input(pin.inputBinding()))
                .field("sourceArtifact", Canonical.sourceArtifact(pin.artifactPin()))
                .build();
    }

    static String internalDependency(ProviderLocalTaskDependency dependency) {
        return new CanonicalWriter()
                .tag("roadmap22.provider-local-task-dependency.v1")
                .field("taskId", dependency.taskId().sha256Hex())
                .field("producerUnitId", dependency.producerUnitId().value())
                .field("consumerUnitId", dependency.consumerUnitId().value())
                .field("sourceDependency", dependency(dependency.sourceDependency()))
                .build();
    }

    static String taskDependency(ExecutableTaskDependency dependency) {
        return new CanonicalWriter()
                .tag("roadmap22.executable-task-dependency.v1")
                .field("producerTaskId", dependency.producerTaskId().sha256Hex())
                .field("consumerTaskId", dependency.consumerTaskId().sha256Hex())
                .field("sourceDependency", dependency(dependency.sourceDependency()))
                .build();
    }

    static String mandatoryArtifactBoundary(MandatoryArtifactBoundary boundary) {
        List<String> downstream = boundary.downstreamDependencies().stream()
                .map(ExecutableTaskCanonicalCodec::dependency)
                .sorted()
                .toList();
        return new CanonicalWriter()
                .tag("roadmap22.mandatory-artifact-boundary.v1")
                .field("producerUnitId", boundary.producerUnitId().value())
                .field("outputDeclaration", output(boundary.outputDeclaration()))
                .field("materializationRequirement",
                        Canonical.materialization(boundary.materializationRequirement()))
                .field("downstreamDependencies", list(downstream))
                .build();
    }

    static String providerTransition(ProviderCompatibilityTransition transition) {
        return new CanonicalWriter()
                .tag("roadmap22.selected-provider-transition.v1")
                .field("sourceDependency", dependency(transition.sourceDependency()))
                .field("producerUnitId", transition.producerUnit().stepId().value())
                .field("producerBinding", new String(
                        ProviderCanonicalCodec.serialize(transition.producerBindingPin()),
                        StandardCharsets.UTF_8))
                .field("consumerUnitId", transition.consumerUnit().stepId().value())
                .field("consumerBinding", new String(
                        ProviderCanonicalCodec.serialize(transition.consumerBindingPin()),
                        StandardCharsets.UTF_8))
                .field("decision", transition.decision().name())
                .field("boundaryContract", optional(
                        "BoundaryContractId",
                        transition.boundaryContractId().map(value -> value.value()).orElse(null)))
                .build();
    }

    static String executionArtifactBoundary(ExecutionArtifactBoundary boundary) {
        return new CanonicalWriter()
                .tag("roadmap22.execution-artifact-boundary.v1")
                .field("sourceDependency", dependency(boundary.sourceDependency()))
                .field("producerUnitId", boundary.producerUnitId().value())
                .field("consumerUnitId", boundary.consumerUnitId().value())
                .field("producerBinding", new String(
                        ProviderCanonicalCodec.serialize(boundary.producerBindingPin()),
                        StandardCharsets.UTF_8))
                .field("consumerBinding", new String(
                        ProviderCanonicalCodec.serialize(boundary.consumerBindingPin()),
                        StandardCharsets.UTF_8))
                .field("producerOutput", output(boundary.producerOutput()))
                .field("consumerInput", input(boundary.consumerInput()))
                .field("materializationContract", boundary.materializationContract().name())
                .field("materializationReason", boundary.reason().name())
                .field("interoperabilityContract", optional(
                        "BoundaryContractId",
                        boundary.interoperabilityContract()
                                .map(value -> value.value()).orElse(null)))
                .build();
    }

    static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String providerBinding(CompositionDecision decision) {
        return new String(
                ProviderCanonicalCodec.serialize(decision.providerBindingPin()),
                StandardCharsets.UTF_8);
    }

    private static String compositionDecision(CompositionDecision decision) {
        List<String> blockers = decision.blockers().stream().map(Enum::name).sorted().toList();
        return new CanonicalWriter()
                .tag("roadmap22.provider-local-composition-decision.v1")
                .field("status", decision.status().name())
                .field("blockers", list(blockers))
                .build();
    }

    private static String physicalPlanUnit(PhysicalPlanUnit unit) {
        List<String> inputs = unit.typedInputs().stream()
                .map(ExecutableTaskCanonicalCodec::input).sorted().toList();
        List<String> outputs = unit.typedOutputs().stream()
                .map(ExecutableTaskCanonicalCodec::output).sorted().toList();
        List<String> dependencies = unit.typedDependencies().stream()
                .map(ExecutableTaskCanonicalCodec::dependency).sorted().toList();
        List<String> capabilities = unit.capabilityRequirementRefs().stream()
                .map(reference -> Canonical.capability(reference.declaration())).sorted().toList();
        List<String> intents = unit.executionIntentRefs().stream()
                .map(reference -> Canonical.executionIntent(reference.declaration())).sorted().toList();
        return new CanonicalWriter()
                .tag("roadmap22.physical-plan-unit-reference.v1")
                .field("stepId", unit.stepId().value())
                .field("logicalNodeId", unit.logicalNodeId())
                .field("sourceRenderNodeId", unit.sourceRenderNodeId().value())
                .field("sourceRenderNodeKind", Canonical.renderNodeKind(unit.sourceRenderNodeKind()))
                .field("operationKey", optional("String", unit.operationKey()))
                .field("typedInputs", list(inputs))
                .field("typedOutputs", list(outputs))
                .field("typedDependencies", list(dependencies))
                .field("temporalWindow", sampleWindow(unit.temporalWindow()))
                .field("executionCoverage", coverage(unit.executionCoverage()))
                .field("capabilityRequirementRefs", list(capabilities))
                .field("executionIntentRefs", list(intents))
                .field("propagatedExtent", extent(unit.propagatedExtent()))
                .field("deterministicallyCacheable",
                        Boolean.toString(unit.deterministicallyCacheable()))
                .build();
    }

    private static String input(InputBinding input) {
        return new CanonicalWriter()
                .tag("roadmap22.input-binding.v1")
                .field("inputId", input.inputId().value())
                .field("consumerLogicalNodeId", input.consumerLogicalNodeId())
                .field("consumerStepId", optional(input.consumerStepId()))
                .field("consumerRenderNodeId", optional(input.consumerRenderNodeId()))
                .field("producerLogicalNodeId", optional("String", input.producerLogicalNodeId()))
                .field("producerStepId", optional(input.producerStepId()))
                .field("producerRenderNodeId", optional(input.producerRenderNodeId()))
                .field("dependencyVariant", optional(
                        "RenderDependency",
                        input.dependencyVariant() == null
                                ? null : Canonical.dependency(input.dependencyVariant())))
                .field("sourceArtifact", optional(
                        "SourceArtifact",
                        input.sourceArtifact() == null
                                ? null : Canonical.sourceArtifact(input.sourceArtifact())))
                .field("requiredSampleWindow", sampleWindow(input.requiredSampleWindow()))
                .build();
    }

    private static String output(OutputDeclaration output) {
        List<String> outputRequirements = output.outputRequirements().stream()
                .map(Canonical::outputRequirement).sorted().toList();
        List<String> materializations = output.materializationRequirements().stream()
                .map(Canonical::materialization).sorted().toList();
        List<String> intermediateArtifacts = output.intermediateArtifactExpectations().stream()
                .map(Canonical::intermediateArtifact).sorted().toList();
        List<String> finalArtifacts = output.finalArtifactExpectations().stream()
                .map(Canonical::finalArtifact).sorted().toList();
        return new CanonicalWriter()
                .tag("roadmap22.output-declaration.v1")
                .field("outputId", output.outputId().value())
                .field("logicalNodeId", output.logicalNodeId())
                .field("sourceRenderNodeId", output.sourceRenderNodeId().value())
                .field("outputRequirements", list(outputRequirements))
                .field("materializationRequirements", list(materializations))
                .field("intermediateArtifactExpectations", list(intermediateArtifacts))
                .field("finalArtifactExpectations", list(finalArtifacts))
                .build();
    }

    private static String dependency(LogicalDependencyEdge dependency) {
        return new CanonicalWriter()
                .tag("roadmap22.logical-dependency-edge.v1")
                .field("edgeId", dependency.edgeId().value())
                .field("producerLogicalNodeId", dependency.producerLogicalNodeId())
                .field("consumerLogicalNodeId", dependency.consumerLogicalNodeId())
                .field("producerRenderNodeId", optional(dependency.producerRenderNodeId()))
                .field("consumerRenderNodeId", optional(dependency.consumerRenderNodeId()))
                .field("dependencyVariant", Canonical.dependency(dependency.dependencyVariant()))
                .build();
    }

    private static String boundaryTarget(BoundaryAction.Target target) {
        if (target instanceof BoundaryAction.RequiredInputArtifactTarget required) {
            return new CanonicalWriter()
                    .tag("RequiredInputArtifactTarget")
                    .field("memberUnitId", required.memberUnitId().value())
                    .field("inputBinding", input(required.inputBinding()))
                    .build();
        }
        if (target instanceof BoundaryAction.IntermediateArtifactTarget intermediate) {
            return new CanonicalWriter()
                    .tag("IntermediateArtifactTarget")
                    .field("memberUnitId", intermediate.memberUnitId().value())
                    .field("outputDeclaration", output(intermediate.outputDeclaration()))
                    .field("artifactTarget", Canonical.intermediateArtifact(intermediate.artifactTarget()))
                    .build();
        }
        if (target instanceof BoundaryAction.FinalArtifactTarget finalTarget) {
            return new CanonicalWriter()
                    .tag("FinalArtifactTarget")
                    .field("memberUnitId", finalTarget.memberUnitId().value())
                    .field("outputDeclaration", output(finalTarget.outputDeclaration()))
                    .field("artifactTarget", Canonical.finalArtifact(finalTarget.artifactTarget()))
                    .build();
        }
        if (target instanceof BoundaryAction.MandatoryMaterializationTarget materialization) {
            String dependency = materialization.dependencyTarget()
                    .map(ExecutableTaskCanonicalCodec::dependency)
                    .orElse(null);
            return new CanonicalWriter()
                    .tag("MandatoryMaterializationTarget")
                    .field("memberUnitId", materialization.memberUnitId().value())
                    .field("outputDeclaration", output(materialization.outputDeclaration()))
                    .field("materializationRequirement",
                            Canonical.materialization(materialization.materializationRequirement()))
                    .field("dependencyTarget", optional("LogicalDependencyEdge", dependency))
                    .build();
        }
        if (target instanceof BoundaryAction.ExecutionArtifactMaterializeTarget materialize) {
            return new CanonicalWriter()
                    .tag("ExecutionArtifactMaterializeTarget")
                    .field("boundary", executionArtifactBoundary(materialize.boundary()))
                    .build();
        }
        if (target instanceof BoundaryAction.ExecutionArtifactAcquireTarget acquire) {
            return new CanonicalWriter()
                    .tag("ExecutionArtifactAcquireTarget")
                    .field("boundary", executionArtifactBoundary(acquire.boundary()))
                    .build();
        }
        throw new IllegalStateException("unknown BoundaryAction.Target variant");
    }

    private static String sampleWindow(RenderSampleWindow window) {
        if (window == null) {
            return optional("RenderSampleWindow", null);
        }
        String canonical = new CanonicalWriter().tag("RenderSampleWindow")
                .field("start", mediaTime(window.start()))
                .field("end", mediaTime(window.end()))
                .field("frameRate", frameRate(window.frameRate()))
                .build();
        return optional("RenderSampleWindow", canonical);
    }

    private static String coverage(RenderExecutionCoverage coverage) {
        if (coverage == null) {
            return optional("RenderExecutionCoverage", null);
        }
        String canonical = new CanonicalWriter().tag("RenderExecutionCoverage")
                .field("start", mediaTime(coverage.start()))
                .field("end", mediaTime(coverage.end()))
                .field("frameRate", frameRate(coverage.frameRate()))
                .build();
        return optional("RenderExecutionCoverage", canonical);
    }

    private static String extent(RenderExtent extent) {
        if (extent == null) {
            return optional("RenderExtent", null);
        }
        String canonical = new CanonicalWriter().tag("RenderExtent")
                .field("start", mediaTime(extent.start()))
                .field("end", mediaTime(extent.end()))
                .field("frameRate", frameRate(extent.frameRate()))
                .build();
        return optional("RenderExtent", canonical);
    }

    private static String mediaTime(MediaTime time) {
        return new CanonicalWriter().tag("MediaTime")
                .field("ticks", Long.toString(time.ticks()))
                .field("timeScale", Long.toString(time.timeScale()))
                .build();
    }

    private static String frameRate(FrameRate rate) {
        return new CanonicalWriter().tag("FrameRate")
                .field("numerator", rate.numerator().toString(10))
                .field("denominator", Long.toString(rate.denominator()))
                .build();
    }

    private static String optional(
            com.example.platform.execution.domain.ExecutionStepId value) {
        return optional("ExecutionStepId", value == null ? null : value.value());
    }

    private static String optional(
            com.example.platform.render.domain.renderplan.RenderNodeId value) {
        return optional("RenderNodeId", value == null ? null : value.value());
    }

    private static String optional(String type, String canonicalValue) {
        return new CanonicalWriter()
                .tag("roadmap22.optional.v1")
                .field("type", Objects.requireNonNull(type, "type"))
                .optional(canonicalValue != null, canonicalValue)
                .build();
    }

    private static String list(List<String> values) {
        Objects.requireNonNull(values, "values");
        values.forEach(value -> Objects.requireNonNull(value, "values element"));
        return new CanonicalWriter().list(values).build();
    }

    static Comparator<BoundaryAction> boundaryActionOrder() {
        return Comparator.comparing(BoundaryAction::phase)
                .thenComparingInt(BoundaryAction::deterministicOrder)
                .thenComparing(ExecutableTaskCanonicalCodec::boundaryAction);
    }
}
