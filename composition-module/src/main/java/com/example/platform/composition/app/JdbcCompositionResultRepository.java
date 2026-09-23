package com.example.platform.composition.app;

import com.example.platform.execution.result.PlatformCompletionReference;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public final class JdbcCompositionResultRepository implements CompositionResultRepository {
    private final JdbcTemplate jdbc;
    public JdbcCompositionResultRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public PlatformCompletionReference record(PlatformCompletionReference c, long generation) {
        jdbc.update("""
                insert into platform_execution_result(execution_id,attempt_id,ownership_generation,result_id,storage_receipt,artifact_id,materialization_state,reconciliation_state,created_at,updated_at)
                values(?,?,?,?,?,?,?, ?,current_timestamp,current_timestamp)
                on conflict(execution_id,attempt_id) do update set updated_at=current_timestamp
                where platform_execution_result.ownership_generation=?
                """,
                c.executionId(),c.attemptId(),generation,c.resultId(),c.storageReceipt(),c.artifactCommitId(),state(c.status()),"NONE",generation);
        return find(c.executionId(), c.attemptId()).orElseThrow();
    }
    @Override public Optional<PlatformCompletionReference> find(String execution, String attempt) {
        return jdbc.query("select execution_id,attempt_id,result_id,storage_receipt,artifact_id,materialization_state from platform_execution_result where execution_id=? and attempt_id=?", rs -> rs.next() ? Optional.of(toReference(rs)) : Optional.empty(), execution, attempt);
    }
    @Override public Optional<PlatformCompletionReference> findByIdempotency(String tenant, String key, String hash) {
        return jdbc.query("select r.execution_id,r.attempt_id,r.result_id,r.storage_receipt,r.artifact_id,r.materialization_state from platform_execution_result r join platform_execution_admission a on a.execution_id=r.execution_id where a.tenant_id=? and a.idempotency_key=? and a.request_hash=? order by r.created_at desc limit 1", rs -> rs.next() ? Optional.of(toReference(rs)) : Optional.empty(), tenant,key,hash);
    }
    private static String state(PlatformCompletionReference.Status status) { return switch (status) { case COMPLETED -> "MEDIA_REGISTERED"; case CANCELLED -> "FAILED"; case FAILED, COMPENSATING -> "FAILED"; }; }
    private static PlatformCompletionReference toReference(java.sql.ResultSet rs) throws java.sql.SQLException { String state=rs.getString(6); var status="MEDIA_REGISTERED".equals(state)?PlatformCompletionReference.Status.COMPLETED:("FAILED".equals(state)?PlatformCompletionReference.Status.FAILED:PlatformCompletionReference.Status.COMPENSATING); return new PlatformCompletionReference(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),status); }
    @Override public Optional<CompositionResultRepository.MaterializedResult> findMaterialized(String tenant, String key, String hash) {
        return jdbc.query("select r.execution_id,r.attempt_id,r.storage_receipt,r.output_digest,r.output_length,r.artifact_id,r.media_asset_id,r.source_revision from platform_execution_result r join platform_execution_admission a on a.execution_id=r.execution_id where a.tenant_id=? and a.idempotency_key=? and a.request_hash=? and r.materialization_state='MEDIA_REGISTERED' order by r.created_at desc limit 1", rs -> rs.next() ? Optional.of(new CompositionResultRepository.MaterializedResult(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getString(6),rs.getString(7),rs.getString(8))) : Optional.empty(), tenant,key,hash);
    }
    @Override public void recordMaterialized(String tenant, String key, String hash, CompositionResultRepository.MaterializedResult result, long generation) {
        record(new com.example.platform.execution.result.PlatformCompletionReference(result.executionId(), result.attemptId(),
                result.artifactId(), result.placementId(), result.artifactId(),
                com.example.platform.execution.result.PlatformCompletionReference.Status.COMPLETED), generation);
        jdbc.update("update platform_execution_result r set media_asset_id=?,output_digest=?,output_length=?,source_revision=?,materialization_state='MEDIA_REGISTERED',updated_at=current_timestamp from platform_execution_admission a where a.execution_id=r.execution_id and a.tenant_id=? and a.idempotency_key=? and a.request_hash=? and r.execution_id=? and r.attempt_id=? and r.ownership_generation=?", result.mediaAssetId(),result.digest(),result.length(),result.sourceRevision(),tenant,key,hash,result.executionId(),result.attemptId(),generation);
    }
}
