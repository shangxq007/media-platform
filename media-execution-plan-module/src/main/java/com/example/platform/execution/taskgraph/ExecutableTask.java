package com.example.platform.execution.taskgraph;

import com.example.platform.execution.composition.CompositionDecision;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable provider-bound primary execution aggregate.
 * Placement, runtime, hardware, backend and lifecycle state are deliberately absent.
 */
public final class ExecutableTask {

    private final ExecutableTaskId id;
    private final CompositionDecision compositionDecision;
    private final List<BoundaryAction> boundaryActions;
    private final List<RequiredInputArtifactPin> requiredInputArtifactPins;

    private ExecutableTask(
            ExecutableTaskId id,
            CompositionDecision compositionDecision,
            List<BoundaryAction> boundaryActions,
            List<RequiredInputArtifactPin> requiredInputArtifactPins) {
        this.id = id;
        this.compositionDecision = compositionDecision;
        this.boundaryActions = boundaryActions;
        this.requiredInputArtifactPins = requiredInputArtifactPins;
    }

    public static ExecutableTask create(
            CompositionDecision compositionDecision,
            Collection<BoundaryAction> boundaryActions) {
        Objects.requireNonNull(compositionDecision, "compositionDecision");
        Objects.requireNonNull(boundaryActions, "boundaryActions");
        if (!compositionDecision.evaluatorProvenAllowed()) {
            throw new IllegalArgumentException(
                    "ExecutableTask requires an evaluator-proven ALLOWED provider-local composition decision");
        }

        List<BoundaryAction> canonicalActions = canonicalActions(
                compositionDecision.memberships(),
                compositionDecision.providerBindingPin(),
                boundaryActions);
        List<RequiredInputArtifactPin> inputPins = requiredInputPins(
                compositionDecision.memberships());
        String canonical = ExecutableTaskCanonicalCodec.taskSemantics(
                compositionDecision, canonicalActions, inputPins);
        ExecutableTaskId id = new ExecutableTaskId(
                ExecutableTaskCanonicalCodec.sha256(canonical));
        return new ExecutableTask(
                id,
                compositionDecision,
                canonicalActions,
                inputPins);
    }

    public ExecutableTaskId id() {
        return id;
    }

    /** The sole provider binding authority, derived from the Phase 5 decision. */
    public ProviderBindingPin providerBindingPin() {
        return compositionDecision.providerBindingPin();
    }

    public CompositionDecision compositionDecision() {
        return compositionDecision;
    }

    public List<ExecutableTaskMembership> memberships() {
        return compositionDecision.memberships();
    }

    public List<BoundaryAction> boundaryActions() {
        return boundaryActions;
    }

    public List<RequiredInputArtifactPin> requiredInputArtifactPins() {
        return requiredInputArtifactPins;
    }

    private static List<BoundaryAction> canonicalActions(
            List<ExecutableTaskMembership> memberships,
            ProviderBindingPin providerBindingPin,
            Collection<BoundaryAction> actions) {
        Map<ExecutionStepId, ExecutableTaskMembership> members = new HashMap<>();
        memberships.forEach(member -> members.put(member.physicalPlanUnitId(), member));

        List<BoundaryAction> canonical = new ArrayList<>(actions.size());
        Set<String> positions = new HashSet<>();
        for (BoundaryAction action : actions) {
            Objects.requireNonNull(action, "boundaryActions element");
            ExecutableTaskMembership member = members.get(action.target().memberUnitId());
            if (member == null) {
                throw new IllegalArgumentException(
                        "BoundaryAction target must belong to the primary task membership");
            }
            validateActionTarget(action, member, providerBindingPin);
            String position = action.phase().name() + "\u0000" + action.deterministicOrder();
            if (!positions.add(position)) {
                throw new IllegalArgumentException(
                        "BoundaryAction phase/order must be unique within the primary task");
            }
            canonical.add(action);
        }
        canonical.sort(ExecutableTaskCanonicalCodec.boundaryActionOrder());
        return List.copyOf(canonical);
    }

    private static void validateActionTarget(
            BoundaryAction action,
            ExecutableTaskMembership member,
            ProviderBindingPin providerBindingPin) {
        BoundaryAction.Target target = action.target();
        if (target instanceof BoundaryAction.CrossProviderMaterializeTarget materialize) {
            CrossProviderArtifactBoundary boundary = materialize.boundary();
            if (action.phase() != BoundaryAction.Phase.POST_EXECUTION
                    || !member.outputMapping().contains(boundary.producerOutput())
                    || !member.physicalPlanUnitId().equals(boundary.producerUnitId())
                    || !providerBindingPin.equals(boundary.producerBindingPin())) {
                throw new IllegalArgumentException(
                        "cross-provider materialize action must be producer task-owned POST_EXECUTION semantics");
            }
            return;
        }
        if (target instanceof BoundaryAction.CrossProviderAcquireTarget acquire) {
            CrossProviderArtifactBoundary boundary = acquire.boundary();
            if (action.phase() != BoundaryAction.Phase.PRE_EXECUTION
                    || !member.inputMapping().contains(boundary.consumerInput())
                    || !member.physicalPlanUnitId().equals(boundary.consumerUnitId())
                    || !providerBindingPin.equals(boundary.consumerBindingPin())) {
                throw new IllegalArgumentException(
                        "cross-provider acquire action must be consumer task-owned PRE_EXECUTION semantics");
            }
            return;
        }
        if (target instanceof BoundaryAction.RequiredInputArtifactTarget required) {
            if (action.phase() != BoundaryAction.Phase.PRE_EXECUTION
                    || !member.inputMapping().contains(required.inputBinding())) {
                throw new IllegalArgumentException(
                        "required input BoundaryAction must be PRE_EXECUTION and reference a member input");
            }
            return;
        }

        OutputDeclaration output;
        if (target instanceof BoundaryAction.IntermediateArtifactTarget intermediate) {
            output = intermediate.outputDeclaration();
        } else if (target instanceof BoundaryAction.FinalArtifactTarget finalTarget) {
            output = finalTarget.outputDeclaration();
        } else if (target instanceof BoundaryAction.MandatoryMaterializationTarget materialization) {
            output = materialization.outputDeclaration();
            materialization.dependencyTarget().ifPresent(dependency -> {
                if (!member.dependencyMapping().contains(dependency)) {
                    throw new IllegalArgumentException(
                            "materialization dependency target must reference a member dependency");
                }
            });
        } else {
            throw new IllegalStateException("unknown BoundaryAction.Target variant");
        }
        if (action.phase() != BoundaryAction.Phase.POST_EXECUTION
                || !member.outputMapping().contains(output)) {
            throw new IllegalArgumentException(
                    "declared output BoundaryAction must be POST_EXECUTION and reference a member output");
        }
    }

    private static List<RequiredInputArtifactPin> requiredInputPins(
            List<ExecutableTaskMembership> memberships) {
        List<RequiredInputArtifactPin> pins = new ArrayList<>();
        for (ExecutableTaskMembership member : memberships) {
            for (InputBinding input : member.inputMapping()) {
                if (input.sourceArtifact() != null) {
                    pins.add(new RequiredInputArtifactPin(
                            member.physicalPlanUnitId(), input, input.sourceArtifact()));
                }
            }
        }
        pins.sort(java.util.Comparator.comparing(
                ExecutableTaskCanonicalCodec::requiredInputArtifactPin));
        return List.copyOf(pins);
    }

    /** Immutable source-artifact pin with its exact canonical #21 consumer mapping. */
    public record RequiredInputArtifactPin(
            ExecutionStepId consumerUnitId,
            InputBinding inputBinding,
            SourceArtifact artifactPin) {

        public RequiredInputArtifactPin {
            Objects.requireNonNull(consumerUnitId, "consumerUnitId");
            Objects.requireNonNull(inputBinding, "inputBinding");
            Objects.requireNonNull(artifactPin, "artifactPin");
            if (!consumerUnitId.equals(inputBinding.consumerStepId())
                    || !artifactPin.equals(inputBinding.sourceArtifact())) {
                throw new IllegalArgumentException(
                        "required input pin must retain its exact canonical consumer binding");
            }
        }
    }
}
