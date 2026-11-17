package com.example.platform.operation.batch;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generic-layer contract tests. The fake domain uses only {@code String} state and
 * imports no Timeline type, which is the executable proof that the generic batch
 * contract does not depend on Timeline.
 */
class OperationBatchPlannerTest {

    private static final String BASE_REVISION = "rev-base";
    private static final String BASE_HASH = sha256("start");
    private static final VersionedAggregateRef TARGET =
            new VersionedAggregateRef("TIMELINE", "tenant-1", "project-1", "project-1", "main");

    @Test
    void emptyBatchIsRejected() {
        BatchException failure = assertThrows(BatchException.class,
                () -> new OperationBatchRequest("batch-1", TARGET, BASE_REVISION, List.of(), null));
        assertEquals(BatchErrorCode.EMPTY_BATCH, failure.code());
    }

    @Test
    void mixedBaseIsRejected() {
        OperationRequest differentBase = move(BASE_REVISION, "b".repeat(64));
        BatchException failure = assertThrows(BatchException.class,
                () -> new OperationBatchRequest("batch-1", TARGET, BASE_REVISION,
                        List.of(move(BASE_REVISION, BASE_HASH), differentBase), null));
        assertEquals(BatchErrorCode.MIXED_BASE, failure.code());
        assertEquals(1, failure.stepIndex());
    }

    @Test
    void crossAggregateKindIsRejectedBeforeAnyPlanning() {
        FakeDomain domain = new FakeDomain("TIMELINE");
        VersionedAggregateRef other = new VersionedAggregateRef("OTHER", "tenant-1", "project-1",
                "project-1", "main");
        BatchException failure = assertThrows(BatchException.class,
                () -> planner().plan(request("batch-1", other, move(BASE_REVISION, BASE_HASH)), domain));
        assertEquals(BatchErrorCode.CROSS_AGGREGATE_BATCH_UNSUPPORTED, failure.code());
        assertEquals(0, domain.planCalls);
    }

    @Test
    void orderChangesBatchDigestAndFingerprint() {
        OperationBatchRequest forward = request("batch-1", TARGET,
                move(BASE_REVISION, BASE_HASH), trim(BASE_REVISION, BASE_HASH));
        OperationBatchRequest reversed = request("batch-1", TARGET,
                trim(BASE_REVISION, BASE_HASH), move(BASE_REVISION, BASE_HASH));

        OperationBatchPlan<String> first = planner().plan(forward, new FakeDomain("TIMELINE"));
        OperationBatchPlan<String> second = planner().plan(reversed, new FakeDomain("TIMELINE"));

        assertNotEquals(first.batchDigest(), second.batchDigest());
        assertNotEquals(fingerprint(first), fingerprint(second));
    }

    @Test
    void sameRequestProducesSameDigestAndFingerprint() {
        OperationBatchRequest request = request("batch-1", TARGET,
                move(BASE_REVISION, BASE_HASH), trim(BASE_REVISION, BASE_HASH));
        OperationBatchPlan<String> first = planner().plan(request, new FakeDomain("TIMELINE"));
        OperationBatchPlan<String> second = planner().plan(request, new FakeDomain("TIMELINE"));

        assertEquals(first.batchDigest(), second.batchDigest());
        assertEquals(first.finalCandidateHash(), second.finalCandidateHash());
        assertEquals(fingerprint(first), fingerprint(second));
    }

    @Test
    void laterStepPlansAgainstTheEvolvingCandidate() {
        FakeDomain domain = new FakeDomain("TIMELINE");
        OperationBatchPlan<String> plan = planner().plan(request("batch-1", TARGET,
                move(BASE_REVISION, BASE_HASH), trim(BASE_REVISION, BASE_HASH)), domain);

        assertEquals(List.of("start", "start|timeline.move", "start|timeline.move|timeline.trim"),
                domain.statesSeen);
        assertEquals("start|timeline.move|timeline.trim", plan.finalCandidate());
        assertEquals(2, plan.steps().size());
        assertEquals(0, plan.steps().get(0).index());
        assertEquals("timeline.trim", plan.steps().get(1).definitionId());
        assertNotEquals(plan.steps().get(0).candidateHash(), plan.steps().get(1).candidateHash());
    }

    @Test
    void perStepValidationFailureStopsBeforeAnyFinalState() {
        FakeDomain domain = new FakeDomain("TIMELINE");
        domain.rejectWhenCandidateContains = "timeline.trim";
        BatchException failure = assertThrows(BatchException.class,
                () -> planner().plan(request("batch-1", TARGET,
                        move(BASE_REVISION, BASE_HASH), trim(BASE_REVISION, BASE_HASH)), domain));
        assertEquals(BatchErrorCode.STEP_VALIDATION_FAILED, failure.code());
        assertEquals(1, failure.stepIndex());
        assertEquals(0, domain.commitCalls);
    }

    @Test
    void finalValidationRunsAfterAllSteps() {
        FakeDomain domain = new FakeDomain("TIMELINE");
        domain.rejectFinal = true;
        BatchException failure = assertThrows(BatchException.class,
                () -> planner().plan(request("batch-1", TARGET,
                        move(BASE_REVISION, BASE_HASH), trim(BASE_REVISION, BASE_HASH)), domain));
        assertEquals(BatchErrorCode.FINAL_VALIDATION_FAILED, failure.code());
        assertEquals(1, domain.finalValidationCalls);
    }

    @Test
    void finalStateEqualToBaseIsClassifiedNoOp() {
        FakeDomain domain = new FakeDomain("TIMELINE");
        domain.identityStep = true;
        OperationBatchPlan<String> plan = planner().plan(request("batch-1", TARGET,
                move(BASE_REVISION, BASE_HASH), trim(BASE_REVISION, BASE_HASH)), domain);

        assertTrue(plan.noOp());
        assertEquals("start", plan.finalCandidate());
        assertTrue(plan.outcomes().stream()
                .allMatch(outcome -> BatchStepOutcome.NO_OP.equals(outcome.status())));
    }

    @Test
    void unknownDefinitionsFailClosedAtTheDomainBoundary() {
        FakeDomain domain = new FakeDomain("TIMELINE");
        domain.unsupportedDefinition = "timeline.text.set-content";
        OperationRequest text = new OperationRequest(
                new com.example.platform.operation.operation.OperationDefinitionId(
                        "timeline.text.set-content"),
                OperationDefinition.V1.MOVE.version(),
                new OperationTargetRequest.TimelineTargetRequest("project-1"),
                new OperationParameters.NoParameters(), BASE_REVISION, BASE_HASH, null);
        BatchException failure = assertThrows(BatchException.class,
                () -> planner().plan(request("batch-1", TARGET, text), domain));
        assertEquals(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, failure.code());
        assertEquals(0, failure.stepIndex());
    }

    private static OperationBatchPlanner planner() {
        return new OperationBatchPlanner();
    }

    private static String fingerprint(OperationBatchPlan<?> plan) {
        OperationBatchContext context = OperationBatchContext.preview(
                CanonicalActor.user("actor-1", "tenant-1", Set.of("editor"), "test"), null);
        return OperationBatchDigest.fingerprint(plan, context);
    }

    private static OperationBatchRequest request(String batchId, VersionedAggregateRef target,
                                                 OperationRequest... operations) {
        return new OperationBatchRequest(batchId, target, BASE_REVISION, List.of(operations), "message");
    }

    private static OperationRequest move(String baseRevision, String baseHash) {
        return request(OperationDefinition.V1.MOVE, baseRevision, baseHash);
    }

    private static OperationRequest trim(String baseRevision, String baseHash) {
        return request(OperationDefinition.V1.TRIM, baseRevision, baseHash);
    }

    private static OperationRequest request(OperationDefinition definition,
                                            String baseRevision, String baseHash) {
        return new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.TimelineTargetRequest("project-1"),
                new OperationParameters.NoParameters(), baseRevision, baseHash, null);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    /** Timeline-free fake domain: every planned step appends an observation to a String state. */
    private static final class FakeDomain implements BatchTargetDomain<String> {

        private final String aggregateKind;
        private final List<String> statesSeen = new ArrayList<>();
        private int planCalls;
        private int commitCalls;
        private int finalValidationCalls;
        private String rejectWhenCandidateContains;
        private String unsupportedDefinition;
        private boolean rejectFinal;
        private boolean identityStep;

        private FakeDomain(String aggregateKind) {
            this.aggregateKind = aggregateKind;
        }

        @Override
        public String aggregateKind() {
            return aggregateKind;
        }

        @Override
        public String resolveBase(VersionedAggregateRef target, String baseRevisionId,
                                  String baseContentHash) {
            statesSeen.add("start");
            return "start";
        }

        @Override
        public String digest(String state) {
            return sha256(state);
        }

        @Override
        public StepPlan<String> planStep(OperationBatchRequest batch, OperationRequest operation,
                                         String currentCandidate) {
            planCalls++;
            String definition = operation.definitionId().value();
            if (unsupportedDefinition != null && unsupportedDefinition.equals(definition)) {
                throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, null,
                        definition + " is not supported by the fake domain");
            }
            String next = identityStep ? currentCandidate : currentCandidate + "|" + definition;
            statesSeen.add(next);
            String planDigest = sha256(next + ":" + definition);
            return new StepPlan<>(next, sha256(definition), "fake:" + definition, planDigest);
        }

        @Override
        public void validateStep(int index, String candidate, OperationBatchRequest batch) {
            if (rejectWhenCandidateContains != null
                    && candidate.contains(rejectWhenCandidateContains)) {
                throw new BatchException(BatchErrorCode.STEP_VALIDATION_FAILED, null,
                        "candidate rejected at step " + index);
            }
        }

        @Override
        public void validateFinal(String candidate, OperationBatchRequest batch) {
            finalValidationCalls++;
            if (rejectFinal) {
                throw new BatchException(BatchErrorCode.FINAL_VALIDATION_FAILED, null,
                        "final candidate rejected");
            }
        }

        @Override
        public BatchCommitResult commit(OperationBatchRequest batch, OperationBatchPlan<String> plan,
                                        OperationBatchContext context) {
            commitCalls++;
            return BatchCommitResult.applied(batch.batchId(), plan.batchDigest(),
                    plan.baseRevisionId(), "rev-new", plan.finalCandidateHash(),
                    plan.baseRevisionId(), batch.target().refId(), plan.outcomes(), false);
        }
    }
}
