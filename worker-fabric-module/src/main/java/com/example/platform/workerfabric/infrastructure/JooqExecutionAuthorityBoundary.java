package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.ArtifactCommitEvidence;
import com.example.platform.workerfabric.domain.CompletionAuthorityPort;
import com.example.platform.workerfabric.domain.CompletionDecision;
import com.example.platform.workerfabric.domain.CompletionEvidence;
import com.example.platform.workerfabric.domain.ExecutionAttemptState;
import com.example.platform.workerfabric.domain.ExecutionObservation;
import com.example.platform.workerfabric.domain.ExecutionObservationIngestionPort;
import com.example.platform.workerfabric.domain.ObservedExecutionState;
import com.example.platform.workerfabric.domain.ReservationState;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** PostgreSQL authority for normalized observations and fenced completion. */
@Repository
public class JooqExecutionAuthorityBoundary
        implements ExecutionObservationIngestionPort, CompletionAuthorityPort {

    private final DSLContext dsl;
    private final Clock clock;

    @Autowired
    public JooqExecutionAuthorityBoundary(DSLContext dsl) {
        this(dsl, Clock.systemUTC());
    }

    public JooqExecutionAuthorityBoundary(DSLContext dsl, Clock clock) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public IngestionResult ingest(ExecutionObservation observation) {
        Objects.requireNonNull(observation, "observation");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record duplicate = tx.fetchOne(
                    "select observation_id from wf_execution_observation "
                            + "where observation_id = ? for update",
                    observation.observationId().value());
            if (duplicate != null) {
                return IngestionResult.DUPLICATE_NOOP;
            }

            Record attempt = tx.fetchOne(
                    """
                    select task_id from wf_execution_attempt
                     where attempt_id = ? and generation = ?
                    """,
                    observation.executionAttemptId().value(),
                    observation.ownershipGeneration().value());
            boolean current = false;
            if (attempt != null) {
                Record ownership = tx.fetchOne(
                        """
                        select current_attempt_id, current_generation, claimable
                          from wf_task_ownership where task_id = ? for update
                        """,
                        attempt.get("task_id", String.class));
                current = ownership != null
                        && !ownership.get("claimable", Boolean.class)
                        && observation.executionAttemptId().value().equals(
                                ownership.get("current_attempt_id", String.class))
                        && observation.ownershipGeneration().value()
                                == ownership.get("current_generation", Long.class);
            }
            if (attempt == null) {
                return IngestionResult.RECORDED_STALE_EVIDENCE;
            }
            tx.execute(
                    """
                    insert into wf_execution_observation (
                        observation_id, attempt_id, generation, observed_state,
                        current_evidence, observed_at)
                    values (?, ?, ?, ?, ?, cast(? as timestamptz))
                    """,
                    observation.observationId().value(),
                    observation.executionAttemptId().value(),
                    observation.ownershipGeneration().value(),
                    observation.observedExecutionState().name(),
                    current,
                    databaseTime(observation.observedAt()));
            return current
                    ? IngestionResult.RECORDED_CURRENT_EVIDENCE
                    : IngestionResult.RECORDED_STALE_EVIDENCE;
        });
    }

    @Override
    public CompletionDecision completeIfCurrent(
            CompletionEvidence completionEvidence,
            ArtifactCommitEvidence artifactCommitEvidence) {
        Objects.requireNonNull(completionEvidence, "completionEvidence");
        Objects.requireNonNull(artifactCommitEvidence, "artifactCommitEvidence");
        if (completionEvidence.backendReportedState() != ObservedExecutionState.SUCCEEDED) {
            return CompletionDecision.BACKEND_NOT_SUCCEEDED_REJECTED;
        }
        if (!completionEvidence.expectedOutputValidation().isValid()) {
            return CompletionDecision.EXPECTED_OUTPUT_INVALID_REJECTED;
        }

        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record duplicate = tx.fetchOne(
                    "select completion_event_id from wf_completion_event "
                            + "where completion_event_id = ? for update",
                    completionEvidence.completionEventId().value());
            if (duplicate != null) {
                return CompletionDecision.DUPLICATE_NOOP;
            }

            String attemptId = completionEvidence.backendExecutionHandle()
                    .executionAttemptId()
                    .value();
            long suppliedGeneration = completionEvidence.backendExecutionHandle()
                    .ownershipGeneration()
                    .value();
            Record attempt = tx.fetchOne(
                    """
                    select task_id, generation, state from wf_execution_attempt
                     where attempt_id = ?
                    """,
                    attemptId);
            if (attempt == null) {
                return CompletionDecision.STALE_ATTEMPT_REJECTED;
            }
            String taskId = attempt.get("task_id", String.class);
            if (!completionEvidence.expectedExecutableTaskId().sha256Hex().equals(taskId)) {
                return CompletionDecision.EXPECTED_TASK_MISMATCH_REJECTED;
            }
            if (suppliedGeneration != attempt.get("generation", Long.class)) {
                return CompletionDecision.STALE_GENERATION_REJECTED;
            }

            Record ownership = tx.fetchOne(
                    """
                    select current_attempt_id, current_generation, claimable
                      from wf_task_ownership where task_id = ? for update
                    """,
                    taskId);
            if (ownership == null
                    || suppliedGeneration != ownership.get("current_generation", Long.class)) {
                return CompletionDecision.STALE_GENERATION_REJECTED;
            }
            if (ownership.get("claimable", Boolean.class)
                    || !attemptId.equals(ownership.get("current_attempt_id", String.class))) {
                return CompletionDecision.STALE_ATTEMPT_REJECTED;
            }

            Record lease = JooqOwnershipFencing.lockActiveLeaseForAttempt(
                    tx, attemptId, suppliedGeneration);
            if (!JooqOwnershipFencing.isCurrentOwner(tx, lease)) {
                return CompletionDecision.STALE_ATTEMPT_REJECTED;
            }
            Instant completedAt = clock.instant();
            tx.execute(
                    """
                    insert into wf_completion_event (
                        completion_event_id, task_id, attempt_id, generation,
                        artifact_commit_reference, artifact_committed_at, completed_at)
                    values (?, ?, ?, ?, ?, cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    completionEvidence.completionEventId().value(),
                    taskId,
                    attemptId,
                    suppliedGeneration,
                    artifactCommitEvidence.authorityEvidenceReference(),
                    databaseTime(artifactCommitEvidence.committedAt()),
                    databaseTime(completedAt));
            JooqOwnershipFencing.closeOwnership(
                    tx,
                    lease,
                    ExecutionAttemptState.SUCCEEDED,
                    ReservationState.RECOVERY_HOLD,
                    completedAt);
            return CompletionDecision.COMPLETED;
        });
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
