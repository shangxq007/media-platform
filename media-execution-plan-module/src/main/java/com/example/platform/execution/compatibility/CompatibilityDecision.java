package com.example.platform.execution.compatibility;

import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Typed result of static compatibility evaluation; ordinary incompatibility is data, not an exception. */
public record CompatibilityDecision(
        Status status,
        ExecutionStepId physicalPlanUnitId,
        ProviderBindingPin providerBindingPin,
        List<StaticCompatibilityFailure> reasons,
        List<CompatibilityEvidence> evidence) {

    public CompatibilityDecision {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(physicalPlanUnitId, "physicalPlanUnitId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        reasons = canonicalReasons(reasons);
        evidence = canonicalEvidence(evidence);

        boolean unknown = reasons.contains(StaticCompatibilityFailure.UNKNOWN_STATIC_COMPATIBILITY);
        if (status == Status.COMPATIBLE && (!reasons.isEmpty() || !evidence.isEmpty())) {
            throw new IllegalArgumentException("compatible decision cannot contain failure data");
        }
        if (status == Status.INCOMPATIBLE && (reasons.isEmpty() || unknown)) {
            throw new IllegalArgumentException("incompatible decision requires known typed reasons");
        }
        if (status == Status.UNKNOWN_FAIL_CLOSED && !unknown) {
            throw new IllegalArgumentException("unknown decision requires UNKNOWN_STATIC_COMPATIBILITY");
        }
        if (status != Status.COMPATIBLE) {
            for (CompatibilityEvidence item : evidence) {
                if (!reasons.contains(item.failure())) {
                    throw new IllegalArgumentException("evidence failure must occur in decision reasons");
                }
            }
        }
    }

    public static CompatibilityDecision compatible(
            ExecutionStepId unitId, ProviderBindingPin bindingPin) {
        return new CompatibilityDecision(Status.COMPATIBLE, unitId, bindingPin, List.of(), List.of());
    }

    public static CompatibilityDecision incompatible(
            ExecutionStepId unitId,
            ProviderBindingPin bindingPin,
            List<StaticCompatibilityFailure> reasons,
            List<CompatibilityEvidence> evidence) {
        return new CompatibilityDecision(Status.INCOMPATIBLE, unitId, bindingPin, reasons, evidence);
    }

    public static CompatibilityDecision unknown(
            ExecutionStepId unitId,
            ProviderBindingPin bindingPin,
            List<CompatibilityEvidence> evidence) {
        return new CompatibilityDecision(
                Status.UNKNOWN_FAIL_CLOSED,
                unitId,
                bindingPin,
                List.of(StaticCompatibilityFailure.UNKNOWN_STATIC_COMPATIBILITY),
                evidence);
    }

    public boolean compatible() {
        return status == Status.COMPATIBLE;
    }

    private static List<StaticCompatibilityFailure> canonicalReasons(
            List<StaticCompatibilityFailure> values) {
        Objects.requireNonNull(values, "reasons");
        var copy = new ArrayList<StaticCompatibilityFailure>(values.size());
        for (StaticCompatibilityFailure value : values) {
            copy.add(Objects.requireNonNull(value, "reasons element"));
        }
        copy.sort(Comparator.naturalOrder());
        rejectAdjacentDuplicates(copy, "duplicate static compatibility reason");
        return List.copyOf(copy);
    }

    private static List<CompatibilityEvidence> canonicalEvidence(List<CompatibilityEvidence> values) {
        Objects.requireNonNull(values, "evidence");
        var copy = new ArrayList<CompatibilityEvidence>(values.size());
        for (CompatibilityEvidence value : values) {
            copy.add(Objects.requireNonNull(value, "evidence element"));
        }
        copy.sort(CompatibilityEvidence.CANONICAL_ORDER);
        rejectAdjacentDuplicates(copy, "duplicate compatibility evidence");
        return List.copyOf(copy);
    }

    private static <T> void rejectAdjacentDuplicates(List<T> sorted, String message) {
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).equals(sorted.get(i))) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    public enum Status {
        COMPATIBLE,
        INCOMPATIBLE,
        UNKNOWN_FAIL_CLOSED
    }
}
