package com.example.platform.execution.composition;

import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable static inputs to provider-local execution-lowering evaluation. */
public record ProviderLocalCompositionRequest(
        List<ExecutableTaskMembership> memberships,
        ProviderBindingPin providerBindingPin,
        ProviderCapabilityProfile providerCapabilityProfile,
        ProviderExecutionContract providerExecutionContract,
        ProviderCompositionDeclaration providerCompositionDeclaration,
        List<CompositionBoundaryConstraint> boundaryConstraints) {

    public ProviderLocalCompositionRequest {
        Objects.requireNonNull(memberships, "memberships");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(providerCapabilityProfile, "providerCapabilityProfile");
        Objects.requireNonNull(providerExecutionContract, "providerExecutionContract");
        Objects.requireNonNull(providerCompositionDeclaration, "providerCompositionDeclaration");
        Objects.requireNonNull(boundaryConstraints, "boundaryConstraints");

        memberships = canonicalMemberships(memberships);
        Set<ExecutionStepId> memberIds = memberships.stream()
                .map(ExecutableTaskMembership::physicalPlanUnitId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        var canonicalConstraints = new ArrayList<CompositionBoundaryConstraint>(boundaryConstraints.size());
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
        boundaryConstraints = List.copyOf(canonicalConstraints);
    }

    public static ProviderLocalCompositionRequest of(
            Collection<ExecutableTaskMembership> memberships,
            ProviderBindingPin providerBindingPin,
            ProviderCapabilityProfile providerCapabilityProfile,
            ProviderExecutionContract providerExecutionContract,
            ProviderCompositionDeclaration providerCompositionDeclaration,
            Collection<CompositionBoundaryConstraint> boundaryConstraints) {
        return new ProviderLocalCompositionRequest(
                List.copyOf(memberships),
                providerBindingPin,
                providerCapabilityProfile,
                providerExecutionContract,
                providerCompositionDeclaration,
                List.copyOf(boundaryConstraints));
    }

    private static List<ExecutableTaskMembership> canonicalMemberships(
            List<ExecutableTaskMembership> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("composition requires ONE_OR_MORE memberships");
        }
        Set<ExecutionStepId> seen = new HashSet<>();
        var units = new ArrayList<com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit>(
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
