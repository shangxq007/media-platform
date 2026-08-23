package com.example.platform.execution.composition;

import com.example.platform.execution.composition.FailureAttribution.MemberAttribution;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Typed, immutable, fail-closed result of provider-local composition evaluation. */
public final class CompositionDecision {

    private static final EvaluationProof EVALUATOR_PROOF = new EvaluationProof();

    private final Status status;
    private final ProviderBindingPin providerBindingPin;
    private final List<ExecutableTaskMembership> memberships;
    private final List<CompositionBlocker> blockers;
    private final List<MemberAttribution> memberFailureAttributions;
    private final EvaluationProof evaluationProof;

    /**
     * Constructs typed decision data without claiming evaluator provenance.
     *
     * <p>This remains available for immutable decision transport and validation tests. A
     * multi-membership task carrying such a decision is rejected by the provider-bound graph;
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
                null);
    }

    private CompositionDecision(
            Status status,
            ProviderBindingPin providerBindingPin,
            List<ExecutableTaskMembership> memberships,
            List<CompositionBlocker> blockers,
            List<MemberAttribution> memberFailureAttributions,
            EvaluationProof evaluationProof) {
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
        this.evaluationProof = evaluationProof;
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
        return allowed() && evaluationProof == EVALUATOR_PROOF;
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

    static CompositionDecision allowed(ProviderLocalCompositionRequest request) {
        return decision(Status.ALLOWED, request, List.of());
    }

    static CompositionDecision forbidden(
            ProviderLocalCompositionRequest request, List<CompositionBlocker> blockers) {
        return decision(Status.FORBIDDEN, request, blockers);
    }

    static CompositionDecision unknown(ProviderLocalCompositionRequest request) {
        return decision(
                Status.UNKNOWN_FAIL_CLOSED,
                request,
                List.of(CompositionBlocker.UNKNOWN_PROVIDER_COMPOSITION_SEMANTICS));
    }

    private static CompositionDecision decision(
            Status status,
            ProviderLocalCompositionRequest request,
            List<CompositionBlocker> blockers) {
        List<MemberAttribution> attributions = request.memberships().stream()
                .map(ExecutableTaskMembership::failureAttributionMapping)
                .toList();
        return new CompositionDecision(
                status,
                request.providerBindingPin(),
                request.memberships(),
                blockers,
                attributions,
                EVALUATOR_PROOF);
    }

    public enum Status {
        ALLOWED,
        FORBIDDEN,
        UNKNOWN_FAIL_CLOSED
    }

    /** Opaque identity token: no caller can manufacture evaluator provenance. */
    private static final class EvaluationProof {
        private EvaluationProof() {
        }
    }
}
