package com.example.platform.execution.composition;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderFeasibilityView;
import com.example.platform.execution.compatibility.StaticProviderCompatibilityProof;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable Phase-5 inputs with one exact kernel proof for every membership. */
public final class ProviderLocalCompositionRequest {

    private final List<ExecutableTaskMembership> memberships;
    private final ProviderFeasibilityView feasibilityView;
    private final ProviderCandidate providerCandidate;
    private final ProviderCompositionDeclaration providerCompositionDeclaration;
    private final List<CompositionBoundaryConstraint> boundaryConstraints;
    private final List<StaticProviderCompatibilityProof> staticCompatibilityProofs;

    private ProviderLocalCompositionRequest(
            Collection<ExecutableTaskMembership> memberships,
            ProviderFeasibilityView feasibilityView,
            ProviderCandidate providerCandidate,
            ProviderCompositionDeclaration providerCompositionDeclaration,
            Collection<CompositionBoundaryConstraint> boundaryConstraints) {
        this.memberships = canonicalMemberships(List.copyOf(memberships));
        this.feasibilityView = Objects.requireNonNull(
                feasibilityView, "feasibilityView");
        this.providerCandidate = Objects.requireNonNull(providerCandidate, "providerCandidate");
        this.providerCompositionDeclaration = Objects.requireNonNull(
                providerCompositionDeclaration, "providerCompositionDeclaration");
        Objects.requireNonNull(boundaryConstraints, "boundaryConstraints");

        Set<ExecutionStepId> memberIds = this.memberships.stream()
                .map(ExecutableTaskMembership::physicalPlanUnitId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var canonicalConstraints = new ArrayList<CompositionBoundaryConstraint>(
                boundaryConstraints.size());
        for (CompositionBoundaryConstraint constraint : boundaryConstraints) {
            Objects.requireNonNull(constraint, "boundaryConstraints element");
            if (!memberIds.contains(constraint.upstreamUnitId())
                    || !memberIds.contains(constraint.downstreamUnitId())) {
                throw new IllegalArgumentException(
                        "composition boundary must reference members in this request");
            }
            canonicalConstraints.add(constraint);
        }
        canonicalConstraints.sort(Comparator.comparing(CompositionBoundaryConstraint::canonicalKey));
        for (int i = 1; i < canonicalConstraints.size(); i++) {
            if (canonicalConstraints.get(i - 1).equals(canonicalConstraints.get(i))) {
                throw new IllegalArgumentException("duplicate composition boundary constraint");
            }
        }
        this.boundaryConstraints = List.copyOf(canonicalConstraints);
        this.staticCompatibilityProofs = requireProofs();
    }

    public static ProviderLocalCompositionRequest of(
            Collection<ExecutableTaskMembership> memberships,
            ProviderFeasibilityView feasibilityView,
            ProviderCandidate providerCandidate,
            ProviderCompositionDeclaration providerCompositionDeclaration,
            Collection<CompositionBoundaryConstraint> boundaryConstraints) {
        Objects.requireNonNull(memberships, "memberships");
        return new ProviderLocalCompositionRequest(
                memberships,
                feasibilityView,
                providerCandidate,
                providerCompositionDeclaration,
                boundaryConstraints);
    }

    public List<ExecutableTaskMembership> memberships() {
        return memberships;
    }

    public ProviderFeasibilityView feasibilityView() {
        return feasibilityView;
    }

    public ProviderCandidate providerCandidate() {
        return providerCandidate;
    }

    public ProviderBindingPin providerBindingPin() {
        return providerCandidate.bindingPin();
    }

    public ProviderDescriptor providerDescriptor() {
        return providerCandidate.descriptor();
    }

    public ProviderCapabilityProfile providerCapabilityProfile() {
        return providerCandidate.capabilityProfile();
    }

    public ProviderExecutionContract providerExecutionContract() {
        return providerCandidate.executionContract();
    }

    public ProviderCompositionDeclaration providerCompositionDeclaration() {
        return providerCompositionDeclaration;
    }

    public List<CompositionBoundaryConstraint> boundaryConstraints() {
        return boundaryConstraints;
    }

    public List<StaticProviderCompatibilityProof> staticCompatibilityProofs() {
        return staticCompatibilityProofs;
    }

    /** Defense in depth for evaluator entry even after request construction. */
    void requireStaticFeasibility() {
        List<StaticProviderCompatibilityProof> current = requireProofs();
        if (!current.equals(staticCompatibilityProofs)) {
            throw new IllegalArgumentException(
                    "composition compatibility proof context changed");
        }
    }

    private List<StaticProviderCompatibilityProof> requireProofs() {
        List<StaticProviderCompatibilityProof> proofs = new ArrayList<>(memberships.size());
        for (ExecutableTaskMembership membership : memberships) {
            StaticProviderCompatibilityProof proof = feasibilityView.requireStaticallyFeasible(
                    membership.physicalPlanUnit(), providerCandidate);
            if (!proof.compatibilityRequest().physicalPlanUnit()
                            .equals(membership.physicalPlanUnit())
                    || !proof.providerCandidate().equals(providerCandidate)) {
                throw new IllegalArgumentException(
                        "each membership requires exact static compatibility proof");
            }
            proofs.add(proof);
        }
        if (proofs.size() != memberships.size()) {
            throw new IllegalArgumentException(
                    "every membership requires exactly one static compatibility proof");
        }
        return List.copyOf(proofs);
    }

    private static List<ExecutableTaskMembership> canonicalMemberships(
            List<ExecutableTaskMembership> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("composition requires ONE_OR_MORE memberships");
        }
        Set<ExecutionStepId> seen = new HashSet<>();
        var units = new ArrayList<
                com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit>(
                values.size());
        for (ExecutableTaskMembership membership : values) {
            Objects.requireNonNull(membership, "memberships element");
            if (!seen.add(membership.physicalPlanUnitId())) {
                throw new IllegalArgumentException(
                        "duplicate physical plan unit membership: "
                                + membership.physicalPlanUnitId().value());
            }
            units.add(membership.physicalPlanUnit());
        }
        return ExecutableTaskMembership.canonicalForUnits(units);
    }
}
