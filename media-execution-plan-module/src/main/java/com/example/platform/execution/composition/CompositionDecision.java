package com.example.platform.execution.composition;

import com.example.platform.execution.composition.FailureAttribution.MemberAttribution;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderCompatibilityGraph;
import com.example.platform.execution.compatibility.StaticProviderCompatibilityProof;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Typed, immutable, fail-closed result of provider-local composition evaluation. */
public final class CompositionDecision {

    private final Status status;
    private final ProviderBindingPin providerBindingPin;
    private final List<ExecutableTaskMembership> memberships;
    private final List<CompositionBlocker> blockers;
    private final List<MemberAttribution> memberFailureAttributions;
    private final Object evaluatorProvenance;
    private final ProviderCompatibilityGraph compatibilityGraph;
    private final ProviderCandidate providerCandidate;
    private final List<StaticProviderCompatibilityProof> staticCompatibilityProofs;

    /**
     * Constructs typed decision data without claiming evaluator provenance.
     *
     * <p>This remains available for immutable decision transport and validation tests. A
     * task carrying such a decision is rejected before it can enter the provider-bound graph;
     * only {@link ProviderLocalCompositionEvaluator#evaluate(ProviderLocalCompositionRequest)}
     * can attach the opaque evaluator proof.
     */
    public CompositionDecision(
            Status status,
            ProviderBindingPin providerBindingPin,
            List<ExecutableTaskMembership> memberships,
            List<CompositionBlocker> blockers,
            List<MemberAttribution> memberFailureAttributions) {
        this(
                status,
                providerBindingPin,
                memberships,
                blockers,
                memberFailureAttributions,
                null,
                null,
                null,
                List.of());
    }

    CompositionDecision(
            Status status,
            ProviderBindingPin providerBindingPin,
            List<ExecutableTaskMembership> memberships,
            List<CompositionBlocker> blockers,
            List<MemberAttribution> memberFailureAttributions,
            Object evaluatorProvenance,
            ProviderCompatibilityGraph compatibilityGraph,
            ProviderCandidate providerCandidate,
            List<StaticProviderCompatibilityProof> staticCompatibilityProofs) {
        this.status = Objects.requireNonNull(status, "status");
        this.providerBindingPin = Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(memberships, "memberships");
        Objects.requireNonNull(blockers, "blockers");
        Objects.requireNonNull(memberFailureAttributions, "memberFailureAttributions");
        this.memberships = List.copyOf(memberships);
        this.memberFailureAttributions = List.copyOf(memberFailureAttributions);

        var canonicalBlockers = new ArrayList<CompositionBlocker>(blockers);
        canonicalBlockers.forEach(value -> Objects.requireNonNull(value, "blockers element"));
        canonicalBlockers.sort(Comparator.naturalOrder());
        for (int i = 1; i < canonicalBlockers.size(); i++) {
            if (canonicalBlockers.get(i - 1) == canonicalBlockers.get(i)) {
                throw new IllegalArgumentException("duplicate composition blocker");
            }
        }
        this.blockers = List.copyOf(canonicalBlockers);

        boolean containsUnknown = this.blockers.contains(
                CompositionBlocker.UNKNOWN_PROVIDER_COMPOSITION_SEMANTICS);
        if (status == Status.ALLOWED && !this.blockers.isEmpty()) {
            throw new IllegalArgumentException("ALLOWED composition cannot contain blockers");
        }
        if (status == Status.FORBIDDEN && (this.blockers.isEmpty() || containsUnknown)) {
            throw new IllegalArgumentException("FORBIDDEN composition requires known blockers");
        }
        if (status == Status.UNKNOWN_FAIL_CLOSED
                && !this.blockers.equals(List.of(
                        CompositionBlocker.UNKNOWN_PROVIDER_COMPOSITION_SEMANTICS))) {
            throw new IllegalArgumentException(
                    "UNKNOWN_FAIL_CLOSED requires exactly the unknown composition blocker");
        }
        if (this.memberships.isEmpty()
                || this.memberFailureAttributions.size() != this.memberships.size()) {
            throw new IllegalArgumentException(
                    "each membership requires one typed failure-attribution mapping");
        }
        for (int i = 0; i < this.memberships.size(); i++) {
            if (!this.memberFailureAttributions.get(i).member()
                    .equals(this.memberships.get(i).physicalPlanUnit())) {
                throw new IllegalArgumentException(
                        "failure attribution must reference the membership's canonical unit");
            }
        }
        if (evaluatorProvenance != null
                && !ProviderLocalCompositionEvaluator.isEvaluatorProvenance(
                        evaluatorProvenance)) {
            throw new IllegalArgumentException(
                    "evaluator provenance must be issued by ProviderLocalCompositionEvaluator");
        }
        this.evaluatorProvenance = evaluatorProvenance;
        this.compatibilityGraph = compatibilityGraph;
        this.providerCandidate = providerCandidate;
        this.staticCompatibilityProofs = List.copyOf(staticCompatibilityProofs);
        if (evaluatorProvenance != null) {
            Objects.requireNonNull(compatibilityGraph, "compatibilityGraph");
            Objects.requireNonNull(providerCandidate, "providerCandidate");
            if (this.staticCompatibilityProofs.size() != this.memberships.size()) {
                throw new IllegalArgumentException(
                        "evaluator provenance requires one static proof per membership");
            }
            for (int i = 0; i < this.memberships.size(); i++) {
                StaticProviderCompatibilityProof proof = this.staticCompatibilityProofs.get(i);
                if (!proof.compatibilityRequest().physicalPlanUnit()
                                .equals(this.memberships.get(i).physicalPlanUnit())
                        || !proof.providerCandidate().equals(providerCandidate)) {
                    throw new IllegalArgumentException(
                            "evaluator provenance must bind exact membership/provider semantics");
                }
            }
        } else if (compatibilityGraph != null
                || providerCandidate != null
                || !this.staticCompatibilityProofs.isEmpty()) {
            throw new IllegalArgumentException(
                    "unproven decision cannot carry evaluator proof context");
        }
    }

    public Status status() {
        return status;
    }

    public ProviderBindingPin providerBindingPin() {
        return providerBindingPin;
    }

    public List<ExecutableTaskMembership> memberships() {
        return memberships;
    }

    public List<CompositionBlocker> blockers() {
        return blockers;
    }

    public List<MemberAttribution> memberFailureAttributions() {
        return memberFailureAttributions;
    }

    public boolean allowed() {
        return status == Status.ALLOWED;
    }

    /** True only for an ALLOWED result returned by the canonical evaluator. */
    public boolean evaluatorProvenAllowed() {
        return allowed()
                && ProviderLocalCompositionEvaluator.isEvaluatorProvenance(
                        evaluatorProvenance)
                && compatibilityGraph != null
                && providerCandidate != null
                && staticCompatibilityProofs.size() == memberships.size();
    }

    public ProviderCompatibilityGraph provenCompatibilityGraph() {
        if (!evaluatorProvenAllowed()) {
            throw new IllegalStateException("composition decision is not evaluator-proven ALLOWED");
        }
        return compatibilityGraph;
    }

    public ProviderCandidate provenProviderCandidate() {
        if (!evaluatorProvenAllowed()) {
            throw new IllegalStateException("composition decision is not evaluator-proven ALLOWED");
        }
        return providerCandidate;
    }

    public List<StaticProviderCompatibilityProof> staticCompatibilityProofs() {
        return staticCompatibilityProofs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CompositionDecision that)) {
            return false;
        }
        return status == that.status
                && providerBindingPin.equals(that.providerBindingPin)
                && memberships.equals(that.memberships)
                && blockers.equals(that.blockers)
                && memberFailureAttributions.equals(that.memberFailureAttributions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                status,
                providerBindingPin,
                memberships,
                blockers,
                memberFailureAttributions);
    }

    @Override
    public String toString() {
        return "CompositionDecision[status=" + status
                + ", providerBindingPin=" + providerBindingPin
                + ", memberships=" + memberships
                + ", blockers=" + blockers
                + ", memberFailureAttributions=" + memberFailureAttributions
                + "]";
    }

    public enum Status {
        ALLOWED,
        FORBIDDEN,
        UNKNOWN_FAIL_CLOSED
    }
}
