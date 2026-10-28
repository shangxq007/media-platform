package com.example.platform.composition.app;

import com.example.platform.execution.result.PlatformCompletionReference;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Composition adapter to the shared Execution result store; other source domains are inaccessible. */
@Repository
public class JdbcCompositionResultRepository implements CompositionResultRepository {
    private final JdbcTemplate jdbc;
    public JdbcCompositionResultRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override @Transactional public PlatformCompletionReference record(PlatformCompletionReference c, long generation) {
        int written = jdbc.update("""
                insert into platform_execution_result(execution_id,attempt_id,ownership_generation,result_id,storage_receipt,artifact_id,materialization_state,reconciliation_state,created_at,updated_at)
                select ?,?,?,?,?,?,?,?,current_timestamp,current_timestamp
                from platform_execution_admission a
                where a.execution_id=? and a.source_domain='composition' and a.ownership_generation=?
                on conflict(execution_id,attempt_id) do update set updated_at=current_timestamp
                where platform_execution_result.ownership_generation=excluded.ownership_generation
                  and platform_execution_result.result_id=excluded.result_id
                  and platform_execution_result.storage_receipt=excluded.storage_receipt
                  and platform_execution_result.artifact_id=excluded.artifact_id
                  and platform_execution_result.materialization_state=excluded.materialization_state
                """, c.executionId(),c.attemptId(),generation,c.resultId(),c.storageReceipt(),c.artifactCommitId(),
                state(c.status()),"NONE",c.executionId(),generation);
        if (written != 1) throw new IllegalArgumentException("result does not match current Composition execution or accepted completion");
        return find(c.executionId(), c.attemptId()).orElseThrow();
    }
    @Override public Optional<PlatformCompletionReference> find(String execution, String attempt) {
        return jdbc.query("select r.execution_id,r.attempt_id,r.result_id,r.storage_receipt,r.artifact_id,r.materialization_state from platform_execution_result r join platform_execution_admission a on a.execution_id=r.execution_id where a.source_domain='composition' and r.execution_id=? and r.attempt_id=?", rs -> rs.next() ? Optional.of(toReference(rs)) : Optional.empty(), execution, attempt);
    }
    @Override public Optional<PlatformCompletionReference> findByIdempotency(String tenant, String key, String hash) {
        return jdbc.query("select r.execution_id,r.attempt_id,r.result_id,r.storage_receipt,r.artifact_id,r.materialization_state from platform_execution_result r join platform_execution_admission a on a.execution_id=r.execution_id where a.source_domain='composition' and a.tenant_id=? and a.idempotency_key=? and a.request_hash=? order by r.created_at desc limit 1", rs -> rs.next() ? Optional.of(toReference(rs)) : Optional.empty(), tenant,key,hash);
    }
    private static String state(PlatformCompletionReference.Status status) { return switch (status) { case COMPLETED -> "ARTIFACT_COMMITTED"; case CANCELLED -> "FAILED"; case FAILED, COMPENSATING -> "FAILED"; }; }
    private static PlatformCompletionReference toReference(java.sql.ResultSet rs) throws java.sql.SQLException { String state=rs.getString(6); var status="ARTIFACT_COMMITTED".equals(state)?PlatformCompletionReference.Status.COMPLETED:("FAILED".equals(state)?PlatformCompletionReference.Status.FAILED:PlatformCompletionReference.Status.COMPENSATING); return new PlatformCompletionReference(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),status); }
    @Override public Optional<CompositionResultRepository.MaterializedResult> findMaterialized(String tenant, String key, String hash) {
        // This legacy materialization caller supplies its issuance key as both key/hash. It has
        // no public execution/download HTTP consumer; do not silently redefine that contract here.
        // Exact request equivalence is enforced by admission and findByIdempotency, not this lookup.
        return jdbc.query("select r.execution_id,r.attempt_id,r.storage_receipt,r.output_digest,r.output_length,r.artifact_id from platform_execution_result r join platform_execution_admission a on a.execution_id=r.execution_id where a.source_domain='composition' and a.tenant_id=? and a.idempotency_key=? and r.materialization_state='ARTIFACT_COMMITTED' order by r.created_at desc limit 1", rs -> rs.next() ? Optional.of(new CompositionResultRepository.MaterializedResult(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getString(6))) : Optional.empty(), tenant,key);
    }
    @Override @Transactional public void recordMaterialized(String tenant, String key, String hash, CompositionResultRepository.MaterializedResult result, long generation) {
        var owned = jdbc.queryForObject("select count(*) from platform_execution_admission where source_domain='composition' and tenant_id=? and idempotency_key=? and execution_id=? and ownership_generation=?", Integer.class,tenant,key,result.executionId(),generation);
        if (owned == null || owned != 1) throw new IllegalArgumentException("materialization does not match current Composition execution");
        record(new PlatformCompletionReference(result.executionId(), result.attemptId(), result.artifactId(), result.placementId(), result.artifactId(), PlatformCompletionReference.Status.COMPLETED), generation);
        int written = jdbc.update("""
                update platform_execution_result r set output_digest=?,output_length=?,materialization_state='ARTIFACT_COMMITTED',updated_at=current_timestamp
                from platform_execution_admission a
                where a.execution_id=r.execution_id and a.source_domain='composition' and a.tenant_id=? and a.idempotency_key=?
                  and r.execution_id=? and r.attempt_id=? and r.ownership_generation=? and a.ownership_generation=?
                  and (r.output_digest is null or r.output_digest=?) and (r.output_length is null or r.output_length=?)
                """, result.digest(),result.length(),tenant,key,result.executionId(),result.attemptId(),generation,generation,result.digest(),result.length());
        if (written != 1) throw new IllegalArgumentException("materialization conflicts with accepted Composition output");
    }
}
