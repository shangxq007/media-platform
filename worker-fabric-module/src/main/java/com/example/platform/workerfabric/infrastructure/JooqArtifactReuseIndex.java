package com.example.platform.workerfabric.infrastructure;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.workerfabric.domain.CompletionEvidence;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.reuse.ArtifactReuseIndexPort;
import com.example.platform.workerfabric.reuse.ReusableArtifactPublication;
import com.example.platform.workerfabric.reuse.ReusableArtifactRecord;
import com.example.platform.workerfabric.reuse.ReusePublicationResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** PostgreSQL persistent reuse index with tenant isolation and ownership/completion fencing. */
@Repository
public final class JooqArtifactReuseIndex implements ArtifactReuseIndexPort {

    private final DSLContext dsl;

    public JooqArtifactReuseIndex(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public Optional<ReusableArtifactRecord> lookup(
            String tenantId,
            ExecutionReuseKey executionReuseKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(executionReuseKey, "executionReuseKey");
        Record row = dsl.fetchOne(
                """
                select * from wf_artifact_reuse_index
                 where tenant_id = ? and reuse_key_version = ? and reuse_key_digest = ?
                   and publication_status = 'WINNING'
                """,
                tenantId,
                executionReuseKey.version(),
                executionReuseKey.stableDigest());
        return Optional.ofNullable(row).map(JooqArtifactReuseIndex::mapRecord);
    }

    @Override
    public ReusePublicationResult stageWinningPublication(
            ReusableArtifactPublication publication) {
        Objects.requireNonNull(publication, "publication");
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            ReusableArtifactRecord candidate = publication.record();
            if (!currentOwner(tx, candidate)) {
                return ReusePublicationResult.STALE_OWNER_REJECTED;
            }
            if (!committedArtifactMatches(tx, candidate)) {
                return ReusePublicationResult.CONFLICT_REJECTED;
            }
            Record existing = lockKey(tx, candidate);
            if (existing != null) {
                ReusableArtifactRecord indexed = mapRecord(existing);
                if ("WINNING".equals(existing.get("publication_status", String.class))) {
                    return samePublication(indexed, candidate)
                            ? ReusePublicationResult.WINNER_IDEMPOTENT
                            : ReusePublicationResult.CONFLICT_REJECTED;
                }
                if (samePublication(indexed, candidate)) {
                    return ReusePublicationResult.PENDING_IDEMPOTENT;
                }
                tx.execute(
                        """
                        update wf_artifact_reuse_index
                           set reuse_key_canonical = ?, artifact_id = ?,
                               artifact_digest_algorithm = ?, artifact_digest_value = ?,
                               task_id = ?, attempt_id = ?, generation = ?,
                               published_at = cast(? as timestamptz)
                         where tenant_id = ? and reuse_key_version = ? and reuse_key_digest = ?
                           and publication_status = 'PENDING'
                        """,
                        candidate.executionReuseKey().canonicalSerialization(),
                        candidate.artifactPin().artifactId().value(),
                        candidate.artifactPin().contentDigest().algorithm().name(),
                        candidate.artifactPin().contentDigest().canonicalValue(),
                        candidate.executableTaskId().sha256Hex(),
                        candidate.executionAttemptId().value(),
                        candidate.ownershipGeneration().value(),
                        databaseTime(candidate.publishedAt()),
                        candidate.tenantId(),
                        candidate.executionReuseKey().version(),
                        candidate.executionReuseKey().stableDigest());
                return ReusePublicationResult.STAGED_PENDING;
            }
            tx.execute(
                    """
                    insert into wf_artifact_reuse_index (
                        tenant_id, reuse_key_version, reuse_key_digest, reuse_key_canonical,
                        artifact_id, artifact_digest_algorithm, artifact_digest_value,
                        task_id, attempt_id, generation, publication_status, published_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', cast(? as timestamptz))
                    """,
                    candidate.tenantId(),
                    candidate.executionReuseKey().version(),
                    candidate.executionReuseKey().stableDigest(),
                    candidate.executionReuseKey().canonicalSerialization(),
                    candidate.artifactPin().artifactId().value(),
                    candidate.artifactPin().contentDigest().algorithm().name(),
                    candidate.artifactPin().contentDigest().canonicalValue(),
                    candidate.executableTaskId().sha256Hex(),
                    candidate.executionAttemptId().value(),
                    candidate.ownershipGeneration().value(),
                    databaseTime(candidate.publishedAt()));
            return ReusePublicationResult.STAGED_PENDING;
        });
    }

    @Override
    public ReusePublicationResult activateWinningPublication(
            ReusableArtifactPublication publication,
            CompletionEvidence completionEvidence) {
        Objects.requireNonNull(publication, "publication");
        Objects.requireNonNull(completionEvidence, "completionEvidence");
        ReusableArtifactRecord candidate = publication.record();
        String completionAttempt = completionEvidence.backendExecutionHandle()
                .executionAttemptId().value();
        long completionGeneration = completionEvidence.backendExecutionHandle()
                .ownershipGeneration().value();
        if (!candidate.executableTaskId().equals(completionEvidence.expectedExecutableTaskId())
                || !candidate.executionAttemptId().value().equals(completionAttempt)
                || candidate.ownershipGeneration().value() != completionGeneration) {
            return ReusePublicationResult.COMPLETION_NOT_AUTHORITATIVE_REJECTED;
        }
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            boolean completionExists = tx.fetchExists(
                    DSL.selectOne()
                            .from("wf_completion_event")
                            .join("wf_execution_attempt")
                            .on(DSL.field("wf_execution_attempt.attempt_id")
                                    .eq(DSL.field("wf_completion_event.attempt_id")))
                            .where(DSL.field("wf_completion_event.completion_event_id").eq(
                                    completionEvidence.completionEventId().value()))
                            .and(DSL.field("wf_completion_event.task_id")
                                    .eq(candidate.executableTaskId().sha256Hex()))
                            .and(DSL.field("wf_completion_event.attempt_id")
                                    .eq(candidate.executionAttemptId().value()))
                            .and(DSL.field("wf_completion_event.generation").eq(
                                    candidate.ownershipGeneration().value()))
                            .and(DSL.field("wf_execution_attempt.state").eq("SUCCEEDED")));
            if (!completionExists) {
                return ReusePublicationResult.COMPLETION_NOT_AUTHORITATIVE_REJECTED;
            }
            Record existing = lockKey(tx, candidate);
            if (existing == null || !samePublication(mapRecord(existing), candidate)) {
                return ReusePublicationResult.CONFLICT_REJECTED;
            }
            if ("WINNING".equals(existing.get("publication_status", String.class))) {
                return completionEvidence.completionEventId().value().equals(
                        existing.get("completion_event_id", String.class))
                        ? ReusePublicationResult.WINNER_IDEMPOTENT
                        : ReusePublicationResult.CONFLICT_REJECTED;
            }
            int updated = tx.execute(
                    """
                    update wf_artifact_reuse_index
                       set publication_status = 'WINNING', completion_event_id = ?
                     where tenant_id = ? and reuse_key_version = ? and reuse_key_digest = ?
                       and publication_status = 'PENDING'
                    """,
                    completionEvidence.completionEventId().value(),
                    candidate.tenantId(),
                    candidate.executionReuseKey().version(),
                    candidate.executionReuseKey().stableDigest());
            return updated == 1
                    ? ReusePublicationResult.ACTIVATED_WINNER
                    : ReusePublicationResult.CONFLICT_REJECTED;
        });
    }

    @Override
    public boolean evict(String tenantId, ExecutionReuseKey executionReuseKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(executionReuseKey, "executionReuseKey");
        return dsl.execute(
                """
                delete from wf_artifact_reuse_index
                 where tenant_id = ? and reuse_key_version = ? and reuse_key_digest = ?
                """,
                tenantId,
                executionReuseKey.version(),
                executionReuseKey.stableDigest()) == 1;
    }

    @Override
    public int purgePendingBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return dsl.execute(
                """
                delete from wf_artifact_reuse_index
                 where publication_status = 'PENDING'
                   and published_at < cast(? as timestamptz)
                """,
                databaseTime(cutoff));
    }

    private static boolean currentOwner(DSLContext tx, ReusableArtifactRecord candidate) {
        return tx.fetchExists(
                DSL.selectOne()
                        .from("wf_task_ownership")
                        .join("wf_execution_attempt")
                        .on(DSL.field("wf_execution_attempt.task_id", String.class)
                                .eq(DSL.field("wf_task_ownership.task_id", String.class)))
                        .where(DSL.field("wf_task_ownership.task_id")
                                .eq(candidate.executableTaskId().sha256Hex()))
                        .and(DSL.field("wf_task_ownership.current_attempt_id")
                                .eq(candidate.executionAttemptId().value()))
                        .and(DSL.field("wf_task_ownership.current_generation")
                                .eq(candidate.ownershipGeneration().value()))
                        .and(DSL.field("wf_task_ownership.claimable").eq(false))
                        .and(DSL.field("wf_execution_attempt.attempt_id")
                                .eq(candidate.executionAttemptId().value()))
                        .and(DSL.field("wf_execution_attempt.generation")
                                .eq(candidate.ownershipGeneration().value()))
                        .and(DSL.field("wf_execution_attempt.state")
                                .in("CREATED", "RUNNING")));
    }

    private static boolean committedArtifactMatches(
            DSLContext tx,
            ReusableArtifactRecord candidate) {
        return tx.fetchExists(
                DSL.selectOne()
                        .from("artifact")
                        .where(DSL.field("id").eq(candidate.artifactPin().artifactId().value()))
                        .and(DSL.field("tenant_id").eq(candidate.tenantId()))
                        .and(DSL.field("state").eq("AVAILABLE"))
                        .and(DSL.field("content_digest")
                                .eq(candidate.artifactPin().contentDigest().canonicalValue())));
    }

    private static Record lockKey(DSLContext tx, ReusableArtifactRecord candidate) {
        return tx.fetchOne(
                """
                select * from wf_artifact_reuse_index
                 where tenant_id = ? and reuse_key_version = ? and reuse_key_digest = ?
                 for update
                """,
                candidate.tenantId(),
                candidate.executionReuseKey().version(),
                candidate.executionReuseKey().stableDigest());
    }

    private static boolean samePublication(
            ReusableArtifactRecord first,
            ReusableArtifactRecord second) {
        return first.tenantId().equals(second.tenantId())
                && first.executionReuseKey().equals(second.executionReuseKey())
                && first.artifactPin().equals(second.artifactPin())
                && first.executableTaskId().equals(second.executableTaskId())
                && first.executionAttemptId().equals(second.executionAttemptId())
                && first.ownershipGeneration().equals(second.ownershipGeneration());
    }

    private static ReusableArtifactRecord mapRecord(Record row) {
        ExecutionReuseKey key = new ExecutionReuseKey(
                row.get("reuse_key_version", String.class),
                row.get("reuse_key_canonical", String.class),
                row.get("reuse_key_digest", String.class));
        ContentDigest contentDigest = new ContentDigest(
                ContentDigest.DigestAlgorithm.valueOf(
                        row.get("artifact_digest_algorithm", String.class)),
                row.get("artifact_digest_value", String.class));
        return new ReusableArtifactRecord(
                row.get("tenant_id", String.class),
                key,
                new ArtifactPin(
                        new ArtifactId(row.get("artifact_id", String.class)), contentDigest),
                new ExecutableTaskId(row.get("task_id", String.class)),
                new ExecutionAttemptId(row.get("attempt_id", String.class)),
                new ExecutionOwnershipGeneration(row.get("generation", Long.class)),
                row.get("published_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
