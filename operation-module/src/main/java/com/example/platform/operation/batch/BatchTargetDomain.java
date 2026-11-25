package com.example.platform.operation.batch;

import com.example.platform.operation.operation.OperationRequest;

/**
 * Domain seam for the generic batch orchestration. The generic layer owns
 * ordering, digest identity, CAS/idempotency context and the one-commit contract;
 * a domain supplies base resolution, per-step planning on the evolving candidate,
 * canonical validation, and the final atomic commit through its existing
 * revision authority.
 *
 * @param <S> the domain's canonical aggregate state; opaque to the generic layer
 */
@org.springframework.modulith.NamedInterface("batch")
public interface BatchTargetDomain<S> {

    /** Stable aggregate-kind token, e.g. {@code TIMELINE}. */
    String aggregateKind();

    /** Resolve the exact immutable base for the batch target; never a mutable latest. */
    S resolveBase(VersionedAggregateRef target, String baseRevisionId, String baseContentHash);

    /** Domain canonical content digest of a candidate state. */
    String digest(S state);

    /**
     * Plan one ordered step against the evolving candidate. {@code currentCandidate}
     * is the state produced by all previous steps; the batch base identity passed to
     * the underlying planner stays the original base, never the evolving state.
     */
    StepPlan<S> planStep(OperationBatchRequest batch, OperationRequest operation, S currentCandidate);

    /** Canonical validation of the candidate produced by step {@code index}. */
    void validateStep(int index, S candidate, OperationBatchRequest batch);

    /** Canonical validation of the final candidate before any write. */
    void validateFinal(S candidate, OperationBatchRequest batch);

    /** Commit the validated final candidate as exactly one revision through the existing authority. */
    BatchCommitResult commit(OperationBatchRequest batch, OperationBatchPlan<S> plan,
                             OperationBatchContext context);

    /** Outcome of one domain step: next candidate plus its plan/provenance identity. */
    record StepPlan<S>(
            S candidate,
            String parameterDigest,
            String targetIdentity,
            String stepPlanDigest,
            java.util.List<BatchStepDescriptor.Change> changes) {
        public StepPlan(S candidate, String parameterDigest, String targetIdentity, String stepPlanDigest) {
            this(candidate, parameterDigest, targetIdentity, stepPlanDigest, java.util.List.of());
        }


        public StepPlan {
            changes = java.util.List.copyOf(changes);
            if (candidate == null) {
                throw new IllegalArgumentException("step candidate required");
            }
            requireText(parameterDigest, "parameterDigest");
            requireText(targetIdentity, "targetIdentity");
            requireText(stepPlanDigest, "stepPlanDigest");
        }

        private static void requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " required");
            }
        }
    }
}
