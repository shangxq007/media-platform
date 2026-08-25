package com.example.platform.workerfabric.reuse;

import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderCompatibilityGraph;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.composition.ProviderCompositionDeclaration;
import com.example.platform.execution.composition.ProviderCompositionDeclaration.NativePipelineSupport;
import com.example.platform.execution.composition.ProviderLocalCompositionEvaluator;
import com.example.platform.execution.composition.ProviderLocalCompositionRequest;
import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutionArtifactBoundary;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.render.domain.renderplan.LogicalArtifactId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RuntimeClosedLoopGraphFixture {

    private static final BoundaryContractId DIRECT_CONTRACT =
            BoundaryContractId.of("phase16-runtime-test-direct.v1");

    private RuntimeClosedLoopGraphFixture() {}

    static ProviderBoundExecutableTaskGraph single(
            String semantic,
            String sourceDigest,
            String provider) {
        PhysicalPlanUnit unit = sourceUnit(semantic, sourceDigest);
        PhysicalExecutionPlan plan = plan(List.of(unit));
        Context context = context(plan, provider);
        return ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(task(context, provider, unit)), List.of());
    }

    static ProviderBoundExecutableTaskGraph sharedDependency(
            String sourceDigest,
            String provider) {
        LogicalDependencyEdge edgeAB = edge("a-b", "unit-a", "unit-b");
        LogicalDependencyEdge edgeAC = edge("a-c", "unit-a", "unit-c");
        PhysicalPlanUnit unitA = unit(
                "unit-a",
                List.of(sourceInput("unit-a", sourceDigest)),
                List.of(output("unit-a")),
                List.of(edgeAB, edgeAC));
        PhysicalPlanUnit unitB = unit(
                "unit-b",
                List.of(computedInput("unit-a", "unit-b", edgeAB)),
                List.of(output("unit-b")),
                List.of(edgeAB));
        PhysicalPlanUnit unitC = unit(
                "unit-c",
                List.of(computedInput("unit-a", "unit-c", edgeAC)),
                List.of(output("unit-c")),
                List.of(edgeAC));
        PhysicalExecutionPlan plan = plan(List.of(unitA, unitB, unitC));
        Context context = context(plan, provider);
        ExecutableTask taskA = task(context, provider, unitA);
        ExecutableTask taskB = task(context, provider, unitB);
        ExecutableTask taskC = task(context, provider, unitC);
        ProviderCandidate candidate = context.candidate();
        return ProviderBoundExecutableTaskGraph.derive(
                plan,
                context.graph(),
                List.of(taskA, taskB, taskC),
                List.of(
                        boundary(edgeAB, unitA, unitB, candidate.bindingPin()),
                        boundary(edgeAC, unitA, unitC, candidate.bindingPin())));
    }

    static ProviderBoundExecutableTaskGraph distinctSourceInputs(
            String sourceXDigest,
            String sourceYDigest,
            String provider) {
        PhysicalPlanUnit unit = unit(
                "unit-distinct-sources",
                List.of(
                        sourceInput("input-a", "unit-distinct-sources", "source-x", sourceXDigest),
                        sourceInput("input-b", "unit-distinct-sources", "source-y", sourceYDigest)),
                List.of(output("unit-distinct-sources")),
                List.of());
        PhysicalExecutionPlan plan = plan(List.of(unit));
        Context context = context(plan, provider);
        return ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(task(context, provider, unit)), List.of());
    }

    static ProviderBoundExecutableTaskGraph sameSourceForTwoRoles(
            String sourceDigest,
            String provider) {
        PhysicalPlanUnit unit = unit(
                "unit-two-roles",
                List.of(
                        sourceInput(
                                "input-foreground", "unit-two-roles", "source-x", sourceDigest),
                        sourceInput("input-mask", "unit-two-roles", "source-x", sourceDigest)),
                List.of(output("unit-two-roles")),
                List.of());
        PhysicalExecutionPlan plan = plan(List.of(unit));
        Context context = context(plan, provider);
        return ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(task(context, provider, unit)), List.of());
    }

    static ProviderBoundExecutableTaskGraph twoComputedInputs(
            String sourceADigest,
            String sourceBDigest,
            String provider) {
        LogicalDependencyEdge edgeAC = edge("a-c-1", "unit-a", "unit-c");
        LogicalDependencyEdge edgeBC = edge("b-c-2", "unit-b", "unit-c");
        PhysicalPlanUnit unitA = unit(
                "unit-a",
                List.of(sourceInput("unit-a", sourceADigest)),
                List.of(output("unit-a")),
                List.of(edgeAC));
        PhysicalPlanUnit unitB = unit(
                "unit-b",
                List.of(sourceInput("unit-b", sourceBDigest)),
                List.of(output("unit-b")),
                List.of(edgeBC));
        PhysicalPlanUnit unitC = unit(
                "unit-c",
                List.of(
                        computedInput("input-c-1", "unit-a", "unit-c", edgeAC),
                        computedInput("input-c-2", "unit-b", "unit-c", edgeBC)),
                List.of(output("unit-c")),
                List.of(edgeAC, edgeBC));
        PhysicalExecutionPlan plan = plan(List.of(unitA, unitB, unitC));
        Context context = context(plan, provider);
        ExecutableTask taskA = task(context, provider, unitA);
        ExecutableTask taskB = task(context, provider, unitB);
        ExecutableTask taskC = task(context, provider, unitC);
        return ProviderBoundExecutableTaskGraph.derive(
                plan,
                context.graph(),
                List.of(taskA, taskB, taskC),
                List.of(
                        boundary(edgeAC, unitA, unitC, context.candidate().bindingPin()),
                        boundary(edgeBC, unitB, unitC, context.candidate().bindingPin())));
    }

    private static ExecutionArtifactBoundary boundary(
            LogicalDependencyEdge edge,
            PhysicalPlanUnit producer,
            PhysicalPlanUnit consumer,
            ProviderBindingPin binding) {
        return new ExecutionArtifactBoundary(
                edge,
                producer.stepId(),
                consumer.stepId(),
                binding,
                binding,
                producer.typedOutputs().getFirst(),
                consumer.typedInputs().stream()
                        .filter(input -> edge.producerLogicalNodeId()
                                .equals(input.producerLogicalNodeId()))
                        .filter(input -> edge.dependencyVariant().equals(input.dependencyVariant()))
                        .findFirst()
                        .orElseThrow(),
                ExecutionArtifactBoundary.MaterializationContract.IMMUTABLE_ARTIFACT_AUTHORITY_V1,
                ExecutionArtifactBoundary.MaterializationReason.INTER_TASK_RUNTIME_BOUNDARY_UNPROVEN,
                Optional.empty());
    }

    private static ExecutableTask task(Context context, String provider, PhysicalPlanUnit unit) {
        var memberships = ExecutableTaskMembership.canonicalForUnits(List.of(unit));
        var request = ProviderLocalCompositionRequest.of(
                memberships,
                context.graph(),
                context.candidate(),
                new ProviderCompositionDeclaration(
                        context.candidate().bindingPin(), NativePipelineSupport.SUPPORTED),
                List.of());
        return ExecutableTask.create(ProviderLocalCompositionEvaluator.evaluate(request), List.of());
    }

    private static Context context(PhysicalExecutionPlan plan, String provider) {
        ProviderCandidate candidate = candidate(provider);
        ProviderCompatibilityGraph graph = ProviderCompatibilityGraph.build(
                plan,
                plan.units().stream().map(CompatibilityRequest::forUnit).toList(),
                List.of(candidate),
                List.of());
        return new Context(graph, candidate);
    }

    private static ProviderCandidate candidate(String provider) {
        ProviderBindingPin binding = binding(provider);
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(
                binding.providerCapabilityProfileVersionOrDigest(), List.of());
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1),
                binding.providerExecutionContractVersion(),
                List.of());
        ProviderDescriptor descriptor = new ProviderDescriptor(
                binding.providerId(),
                binding.providerImplementationId(),
                binding.providerVersion(),
                binding.providerExecutionContractVersion(),
                binding.providerCapabilityProfileVersionOrDigest());
        ProviderStaticCompatibility compatibility = new ProviderStaticCompatibility(
                ProviderStaticCompatibility.Knowledge.DECLARED,
                List.of(
                        ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,
                        ProviderStaticCompatibility.ArtifactRequirementKind.MANDATORY_MATERIALIZATION,
                        ProviderStaticCompatibility.ArtifactRequirementKind.INTERMEDIATE_OUTPUT,
                        ProviderStaticCompatibility.ArtifactRequirementKind.FINAL_OUTPUT),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(DIRECT_CONTRACT),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
        return new ProviderCandidate(binding, descriptor, contract, profile, compatibility);
    }

    private static ProviderBindingPin binding(String provider) {
        return new ProviderBindingPin(
                ProviderId.of(provider),
                ProviderImplementationId.of(provider + ".native"),
                ProviderVersion.of("1.0.0"),
                ProviderExecutionContractVersion.of(1, 0),
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0)),
                List.of());
    }

    private static PhysicalExecutionPlan plan(List<PhysicalPlanUnit> units) {
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("phase16-runtime-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("phase16-runtime-fingerprint"),
                units,
                null,
                new PhysicalExecutionPlanDigest("phase16-runtime-plan-digest"));
    }

    private static PhysicalPlanUnit sourceUnit(String semantic, String digest) {
        return unit(
                semantic,
                List.of(sourceInput(semantic, digest)),
                List.of(output(semantic)),
                List.of());
    }

    private static InputBinding sourceInput(String unit, String digest) {
        return sourceInput("input-" + unit, unit, "source-" + unit, digest);
    }

    private static InputBinding sourceInput(
            String inputId,
            String consumerUnit,
            String sourceArtifactId,
            String digest) {
        return new InputBinding(
                new ExecutionInputId(inputId),
                "logical-" + consumerUnit,
                new ExecutionStepId(consumerUnit),
                new RenderNodeId("render-" + consumerUnit),
                null, null, null, null,
                new SourceArtifact(
                        new ArtifactId(sourceArtifactId), ContentDigest.sha256(digest)),
                null);
    }

    private static InputBinding computedInput(
            String producer,
            String consumer,
            LogicalDependencyEdge edge) {
        return computedInput("input-" + producer + "-" + consumer, producer, consumer, edge);
    }

    private static InputBinding computedInput(
            String inputId,
            String producer,
            String consumer,
            LogicalDependencyEdge edge) {
        return new InputBinding(
                new ExecutionInputId(inputId),
                "logical-" + consumer,
                new ExecutionStepId(consumer),
                new RenderNodeId("render-" + consumer),
                "logical-" + producer,
                new ExecutionStepId(producer),
                new RenderNodeId("render-" + producer),
                edge.dependencyVariant(),
                null,
                null);
    }

    private static OutputDeclaration output(String unit) {
        return new OutputDeclaration(
                new ExecutionOutputId("output-" + unit),
                "logical-" + unit,
                new RenderNodeId("render-" + unit),
                List.of(), List.of(),
                List.of(new IntermediateArtifactExpectation(
                        new LogicalArtifactId("logical-artifact-" + unit),
                        RenderOutputRole.RENDER_MASTER)),
                List.of());
    }

    private static LogicalDependencyEdge edge(String id, String producer, String consumer) {
        return new LogicalDependencyEdge(
                new ExecutionEdgeId("edge-" + id),
                "logical-" + producer,
                "logical-" + consumer,
                new RenderNodeId("render-" + producer),
                new RenderNodeId("render-" + consumer),
                new RenderDependency.DecodedFrames());
    }

    private static PhysicalPlanUnit unit(
            String id,
            List<InputBinding> inputs,
            List<OutputDeclaration> outputs,
            List<LogicalDependencyEdge> dependencies) {
        return new PhysicalPlanUnit(
                new ExecutionStepId(id),
                "logical-" + id,
                new RenderNodeId("render-" + id),
                new RenderNodeKind.Decode(),
                "decode",
                inputs,
                outputs,
                dependencies,
                null, null,
                List.of(), List.of(),
                null,
                true);
    }

    private record Context(
            ProviderCompatibilityGraph graph,
            ProviderCandidate candidate) {}
}
