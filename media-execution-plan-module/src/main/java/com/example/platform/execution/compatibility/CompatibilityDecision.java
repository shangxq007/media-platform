package com.example.platform.execution.compatibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Typed static compatibility result carrying exact evaluated inputs and optional kernel proof. */
public final class CompatibilityDecision {

    private final Status status;
    private final CompatibilityRequest compatibilityRequest;
    private final ProviderCandidate providerCandidate;
    private final List<StaticCompatibilityFailure> reasons;
    private final List<CompatibilityEvidence> evidence;
    private final StaticProviderCompatibilityProof kernelProof;

    /** Constructs decision transport data without claiming kernel provenance. */
    public CompatibilityDecision(
            Status status,
            CompatibilityRequest compatibilityRequest,
            ProviderCandidate providerCandidate,
            List<StaticCompatibilityFailure> reasons,
            List<CompatibilityEvidence> evidence) {
        this(status, compatibilityRequest, providerCandidate, reasons, evidence, null);
    }

    private CompatibilityDecision(
            Status status,
            CompatibilityRequest compatibilityRequest,
            ProviderCandidate providerCandidate,
            List<StaticCompatibilityFailure> reasons,
            List<CompatibilityEvidence> evidence,
            StaticProviderCompatibilityProof kernelProof) {
        this.status = Objects.requireNonNull(status, "status");
        this.compatibilityRequest = Objects.requireNonNull(compatibilityRequest, "compatibilityRequest");
        this.providerCandidate = Objects.requireNonNull(providerCandidate, "providerCandidate");
        this.reasons = canonicalReasons(reasons);
        this.evidence = canonicalEvidence(evidence);

        boolean unknown = this.reasons.contains(
                StaticCompatibilityFailure.UNKNOWN_STATIC_COMPATIBILITY);
        if (status == Status.COMPATIBLE && (!this.reasons.isEmpty() || !this.evidence.isEmpty())) {
            throw new IllegalArgumentException("compatible decision cannot contain failure data");
        }
        if (status == Status.INCOMPATIBLE && (this.reasons.isEmpty() || unknown)) {
            throw new IllegalArgumentException("incompatible decision requires known typed reasons");
        }
        if (status == Status.UNKNOWN_FAIL_CLOSED && !unknown) {
            throw new IllegalArgumentException(
                    "unknown decision requires UNKNOWN_STATIC_COMPATIBILITY");
        }
        for (CompatibilityEvidence item : this.evidence) {
            if (!this.reasons.contains(item.failure())) {
                throw new IllegalArgumentException(
                        "evidence failure must occur in decision reasons");
            }
        }
        if (kernelProof != null
                && (status != Status.COMPATIBLE
                        || !kernelProof.proves(compatibilityRequest, providerCandidate))) {
            throw new IllegalArgumentException(
                    "kernel proof must bind the exact compatible request and provider candidate");
        }
        this.kernelProof = kernelProof;
    }

    static CompatibilityDecision kernelCompatible(
            CompatibilityRequest request,
            ProviderCandidate candidate,
            StaticProviderCompatibilityProof proof) {
        return new CompatibilityDecision(
                Status.COMPATIBLE, request, candidate, List.of(), List.of(), proof);
    }

    static CompatibilityDecision incompatible(
            CompatibilityRequest request,
            ProviderCandidate candidate,
            List<StaticCompatibilityFailure> reasons,
            List<CompatibilityEvidence> evidence) {
        return new CompatibilityDecision(
                Status.INCOMPATIBLE, request, candidate, reasons, evidence, null);
    }

    static CompatibilityDecision unknown(
            CompatibilityRequest request,
            ProviderCandidate candidate,
            List<CompatibilityEvidence> evidence) {
        return new CompatibilityDecision(
                Status.UNKNOWN_FAIL_CLOSED,
                request,
                candidate,
                List.of(StaticCompatibilityFailure.UNKNOWN_STATIC_COMPATIBILITY),
                evidence,
                null);
    }

    public Status status() {
        return status;
    }

    public CompatibilityRequest compatibilityRequest() {
        return compatibilityRequest;
    }

    public ProviderCandidate providerCandidate() {
        return providerCandidate;
    }

    public List<StaticCompatibilityFailure> reasons() {
        return reasons;
    }

    public List<CompatibilityEvidence> evidence() {
        return evidence;
    }

    public boolean compatible() {
        return status == Status.COMPATIBLE;
    }

    public boolean kernelProvenCompatible() {
        return compatible()
                && kernelProof != null
                && kernelProof.proves(compatibilityRequest, providerCandidate);
    }

    public Optional<StaticProviderCompatibilityProof> staticCompatibilityProof() {
        return Optional.ofNullable(kernelProof);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CompatibilityDecision that
                && status == that.status
                && compatibilityRequest.equals(that.compatibilityRequest)
                && providerCandidate.equals(that.providerCandidate)
                && reasons.equals(that.reasons)
                && evidence.equals(that.evidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                status, compatibilityRequest, providerCandidate, reasons, evidence);
    }

    @Override
    public String toString() {
        return "CompatibilityDecision[status=" + status
                + ", compatibilityRequest=" + compatibilityRequest
                + ", providerCandidate=" + providerCandidate
                + ", reasons=" + reasons
                + ", evidence=" + evidence
                + ", kernelProvenCompatible=" + kernelProvenCompatible()
                + "]";
    }

    private static List<StaticCompatibilityFailure> canonicalReasons(
            List<StaticCompatibilityFailure> values) {
        Objects.requireNonNull(values, "reasons");
        var copy = new ArrayList<StaticCompatibilityFailure>(values.size());
        values.forEach(value -> copy.add(Objects.requireNonNull(value, "reasons element")));
        copy.sort(Comparator.naturalOrder());
        rejectAdjacentDuplicates(copy, "duplicate static compatibility reason");
        return List.copyOf(copy);
    }

    private static List<CompatibilityEvidence> canonicalEvidence(List<CompatibilityEvidence> values) {
        Objects.requireNonNull(values, "evidence");
        var copy = new ArrayList<CompatibilityEvidence>(values.size());
        values.forEach(value -> copy.add(Objects.requireNonNull(value, "evidence element")));
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
