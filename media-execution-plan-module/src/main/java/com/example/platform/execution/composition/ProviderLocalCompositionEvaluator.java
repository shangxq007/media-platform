package com.example.platform.execution.composition;

import com.example.platform.execution.composition.ProviderCompositionDeclaration.NativePipelineSupport;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure Phase 5 authority for provider-local execution-lowering legality.
 * It does not fuse semantics, rewrite any plan or inspect mutable runtime state.
 */
public final class ProviderLocalCompositionEvaluator {

    /** Frozen hard law: mandatory artifact boundaries are never optimized away. */
    public static final boolean MANDATORY_ARTIFACT_BOUNDARY_BLOCKS_COALESCING = true;

    private ProviderLocalCompositionEvaluator() {
    }

    public static CompositionDecision evaluate(ProviderLocalCompositionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.memberships().size() == 1) {
            return CompositionDecision.allowed(request);
        }

        EnumSet<CompositionBlocker> blockers = EnumSet.noneOf(CompositionBlocker.class);
        Map<ExecutionStepId, PhysicalPlanUnit> units = unitsByStep(request);
        Map<String, PhysicalPlanUnit> unitsByLogicalNode = unitsByLogicalNode(request);

        evaluateMandatoryArtifactBoundaries(units, unitsByLogicalNode, blockers);
        evaluateInputOutputContracts(units, blockers);
        evaluateStaticExecutionRequirements(units.values(), blockers);
        request.boundaryConstraints().forEach(constraint -> blockers.add(constraint.blocker()));

        if (!providerDeclarationsMatch(request)
                || request.providerCompositionDeclaration().nativePipelineSupport()
                        == NativePipelineSupport.UNSUPPORTED) {
            blockers.add(CompositionBlocker.PROVIDER_NATIVE_PIPELINE_UNSUPPORTED);
        }

        if (!blockers.isEmpty()) {
            return CompositionDecision.forbidden(request, List.copyOf(blockers));
        }
        if (request.providerCompositionDeclaration().nativePipelineSupport()
                == NativePipelineSupport.UNKNOWN) {
            return CompositionDecision.unknown(request);
        }
        return CompositionDecision.allowed(request);
    }

    private static boolean providerDeclarationsMatch(ProviderLocalCompositionRequest request) {
        return request.providerCompositionDeclaration().providerBindingPin()
                        .equals(request.providerBindingPin())
                && request.providerCapabilityProfile().reference()
                        .equals(request.providerBindingPin()
                                .providerCapabilityProfileVersionOrDigest())
                && request.providerExecutionContract().contractVersion()
                        .equals(request.providerBindingPin().providerExecutionContractVersion());
    }

    private static void evaluateMandatoryArtifactBoundaries(
            Map<ExecutionStepId, PhysicalPlanUnit> units,
            Map<String, PhysicalPlanUnit> unitsByLogicalNode,
            EnumSet<CompositionBlocker> blockers) {
        for (PhysicalPlanUnit producer : units.values()) {
            if (!hasMandatoryMaterialization(producer)) {
                continue;
            }
            for (PhysicalPlanUnit consumer : units.values()) {
                if (!producer.stepId().equals(consumer.stepId())
                        && hasDependency(producer, consumer, unitsByLogicalNode)) {
                    blockers.add(CompositionBlocker.MANDATORY_INTERMEDIATE_ARTIFACT);
                    return;
                }
            }
        }
    }

    private static boolean hasMandatoryMaterialization(PhysicalPlanUnit unit) {
        return unit.typedOutputs().stream()
                .anyMatch(output -> !output.materializationRequirements().isEmpty());
    }

    private static boolean hasDependency(
            PhysicalPlanUnit producer,
            PhysicalPlanUnit consumer,
            Map<String, PhysicalPlanUnit> unitsByLogicalNode) {
        boolean typedInput = consumer.typedInputs().stream()
                .anyMatch(input -> producer.stepId().equals(input.producerStepId()));
        if (typedInput) {
            return true;
        }
        return consumer.typedDependencies().stream().anyMatch(edge ->
                edge.producerLogicalNodeId().equals(producer.logicalNodeId())
                        && edge.consumerLogicalNodeId().equals(consumer.logicalNodeId())
                        && unitsByLogicalNode.containsKey(edge.producerLogicalNodeId())
                        && unitsByLogicalNode.containsKey(edge.consumerLogicalNodeId()));
    }

    private static void evaluateInputOutputContracts(
            Map<ExecutionStepId, PhysicalPlanUnit> units,
            EnumSet<CompositionBlocker> blockers) {
        for (PhysicalPlanUnit consumer : units.values()) {
            for (InputBinding input : consumer.typedInputs()) {
                boolean internalProducer = input.producerStepId() != null
                        && units.containsKey(input.producerStepId());
                boolean consumerIdentityMismatch = input.consumerStepId() != null
                        && (!input.consumerStepId().equals(consumer.stepId())
                                || !input.consumerLogicalNodeId().equals(consumer.logicalNodeId()));
                if (consumerIdentityMismatch
                        || (internalProducer && input.consumerStepId() == null)) {
                    blockers.add(CompositionBlocker.INPUT_OUTPUT_CONTRACT_INCOMPATIBLE);
                    continue;
                }
                if (!internalProducer) {
                    continue;
                }
                PhysicalPlanUnit producer = units.get(input.producerStepId());
                boolean producerIdentityMatches = input.producerLogicalNodeId() != null
                        && input.producerLogicalNodeId().equals(producer.logicalNodeId());
                boolean outputExists = producer.typedOutputs().stream()
                        .anyMatch(output -> output.logicalNodeId().equals(producer.logicalNodeId()));
                boolean dependencyPreserved = matchingDependency(
                        producer, consumer, input, producer.typedDependencies())
                        && matchingDependency(producer, consumer, input, consumer.typedDependencies());
                if (!producerIdentityMatches || !outputExists || !dependencyPreserved) {
                    blockers.add(CompositionBlocker.INPUT_OUTPUT_CONTRACT_INCOMPATIBLE);
                }
            }
        }
    }

    private static boolean matchingDependency(
            PhysicalPlanUnit producer,
            PhysicalPlanUnit consumer,
            InputBinding input,
            List<LogicalDependencyEdge> dependencies) {
        return input.dependencyVariant() != null && dependencies.stream().anyMatch(edge ->
                edge.producerLogicalNodeId().equals(producer.logicalNodeId())
                        && edge.consumerLogicalNodeId().equals(consumer.logicalNodeId())
                        && edge.dependencyVariant().equals(input.dependencyVariant()));
    }

    private static void evaluateStaticExecutionRequirements(
            Collection<PhysicalPlanUnit> units,
            EnumSet<CompositionBlocker> blockers) {
        Set<List<Boolean>> sandboxProfiles = new HashSet<>();
        Set<List<RenderDeterminismClass>> determinismProfiles = new HashSet<>();
        for (PhysicalPlanUnit unit : units) {
            List<RenderExecutionRequirement> requirements = unit.executionIntentRefs().stream()
                    .map(reference -> reference.declaration())
                    .toList();
            if (requirements.isEmpty()) {
                requirements = List.of(RenderExecutionRequirement.DEFAULT);
            }
            sandboxProfiles.add(requirements.stream()
                    .map(RenderExecutionRequirement::sandboxedIntent)
                    .distinct()
                    .sorted()
                    .toList());
            determinismProfiles.add(requirements.stream()
                    .map(RenderExecutionRequirement::determinism)
                    .distinct()
                    .sorted()
                    .toList());
        }
        if (sandboxProfiles.size() > 1) {
            blockers.add(CompositionBlocker.SANDBOX_OR_TRUST_BOUNDARY);
        }
        if (determinismProfiles.size() > 1) {
            blockers.add(CompositionBlocker.DETERMINISM_INCOMPATIBLE);
        }
    }

    private static Map<ExecutionStepId, PhysicalPlanUnit> unitsByStep(
            ProviderLocalCompositionRequest request) {
        Map<ExecutionStepId, PhysicalPlanUnit> result = new HashMap<>();
        request.memberships().forEach(membership ->
                result.put(membership.physicalPlanUnitId(), membership.physicalPlanUnit()));
        return result;
    }

    private static Map<String, PhysicalPlanUnit> unitsByLogicalNode(
            ProviderLocalCompositionRequest request) {
        Map<String, PhysicalPlanUnit> result = new HashMap<>();
        request.memberships().forEach(membership -> result.put(
                membership.physicalPlanUnit().logicalNodeId(), membership.physicalPlanUnit()));
        return result;
    }

}
