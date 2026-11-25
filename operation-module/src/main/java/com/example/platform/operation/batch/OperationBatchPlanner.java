package com.example.platform.operation.batch;

import com.example.platform.operation.operation.OperationRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generic ordered batch planner: one base, N steps on the evolving candidate, one
 * final validated candidate, one deterministic digest.
 *
 * <p>Intermediate candidates exist only in this call. Nothing is written and no
 * candidate is observable as committed. Any step failure aborts the whole plan.
 */
@org.springframework.modulith.NamedInterface("batch")
public final class OperationBatchPlanner {

    public <S> OperationBatchPlan<S> plan(OperationBatchRequest request, BatchTargetDomain<S> domain) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(domain, "domain");
        if (!domain.aggregateKind().equals(request.target().aggregateKind())) {
            throw new BatchException(BatchErrorCode.CROSS_AGGREGATE_BATCH_UNSUPPORTED, null,
                    "batch aggregate kind " + request.target().aggregateKind()
                            + " is not handled by " + domain.aggregateKind());
        }

        S base = domain.resolveBase(request.target(), request.baseRevisionId(),
                request.baseContentHash());
        S candidate = base;
        String previousHash = request.baseContentHash();
        List<BatchStepDescriptor> steps = new ArrayList<>(request.operations().size());
        List<BatchStepOutcome> outcomes = new ArrayList<>(request.operations().size());
        Boolean completeRevisionNoOp = null;

        for (int index = 0; index < request.operations().size(); index++) {
            OperationRequest operation = request.operations().get(index);
            if (!request.baseRevisionId().equals(operation.baseRevisionId())
                    || !request.baseContentHash().equals(operation.baseContentHash())) {
                throw new BatchException(BatchErrorCode.MIXED_BASE, index,
                        "operation " + index + " does not share the batch base");
            }
            BatchTargetDomain.StepPlan<S> step;
            try {
                step = domain.planStep(request, operation, candidate);
            } catch (BatchException failure) {
                throw failure.stepIndex() == null
                        ? new BatchException(failure.code(), index, failure.getMessage(), failure)
                        : failure;
            } catch (RuntimeException failure) {
                throw new BatchException(BatchErrorCode.STEP_PLAN_FAILED, index,
                        "step " + index + " planning failed: " + failure.getMessage(), failure);
            }
            try {
                domain.validateStep(index, step.candidate(), request);
            } catch (BatchException failure) {
                throw failure.stepIndex() == null
                        ? new BatchException(failure.code(), index, failure.getMessage(), failure)
                        : failure;
            }
            String candidateHash = domain.digest(step.candidate());
            steps.add(new BatchStepDescriptor(index,
                    operation.definitionId().value(), operation.version().toString(),
                    step.parameterDigest(), step.targetIdentity(), step.stepPlanDigest(),
                    candidateHash, step.changes(), operation.parameters() instanceof
                            com.example.platform.operation.operation.OperationParameters.CompositionAspectParameters aspect
                            ? aspect.input() : null));
            if (step.completeRevisionNoOp() != null) {
                if (request.operations().size() != 1) {
                    throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, index,
                            "Complete revision replacement requires a single-operation batch");
                }
                completeRevisionNoOp = step.completeRevisionNoOp();
            }
            boolean stepNoOp = completeRevisionNoOp == null ? candidateHash.equals(previousHash) : completeRevisionNoOp;
            outcomes.add(new BatchStepOutcome(index,
                    stepNoOp ? BatchStepOutcome.NO_OP : BatchStepOutcome.APPLIED,
                    step.stepPlanDigest()));
            previousHash = candidateHash;
            candidate = step.candidate();
        }

        try {
            domain.validateFinal(candidate, request);
        } catch (BatchException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new BatchException(BatchErrorCode.FINAL_VALIDATION_FAILED, null,
                    "final candidate validation failed: " + failure.getMessage(), failure);
        }
        String finalHash = domain.digest(candidate);
        boolean noOp = completeRevisionNoOp == null ? finalHash.equals(request.baseContentHash()) : completeRevisionNoOp;
        String batchDigest = OperationBatchDigest.compute(request, steps, finalHash);
        return new OperationBatchPlan<>(OperationBatchPlan.FORMAT_VERSION, request.batchId(),
                request.target(), request.baseRevisionId(), request.baseContentHash(),
                steps, candidate, finalHash, batchDigest, noOp, outcomes);
    }
}
