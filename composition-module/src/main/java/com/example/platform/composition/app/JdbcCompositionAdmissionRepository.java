package com.example.platform.composition.app;

import com.example.platform.execution.planning.PlatformExecutionPlan;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public final class JdbcCompositionAdmissionRepository implements CompositionAdmissionRepository {
    private final JdbcTemplate jdbc;
    public JdbcCompositionAdmissionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public AdmissionRecord admit(PlatformExecutionPlan plan, String compositionId, long revision) {
        String id = CompositionExecutionIds.of(plan.scope().tenantId(), plan.scope().workspaceId(), plan.idempotency().key());
        int inserted = jdbc.update("""
            insert into platform_execution_admission
              (execution_id, tenant_id, workspace_id, actor_id, source_domain, composition_id,
               composition_revision, plan_id, idempotency_key, request_hash, ownership_generation,
               state, quota_units, quota_charged, created_at, updated_at)
            values (?,?,?,?,?,?,?,?,?,?,0,'ADMITTED',?,false,?,?)
            on conflict (tenant_id, workspace_id, idempotency_key) do nothing
            """, id, plan.scope().tenantId(), plan.scope().workspaceId(), plan.scope().actorId(),
                plan.source().domain(), compositionId, revision, plan.planId().value(),
                plan.idempotency().key(), plan.idempotency().requestHash(), plan.quota().quotaUnits(),
                Instant.now(), Instant.now());
        if (inserted == 0) return find(plan.scope().tenantId(), plan.scope().workspaceId(), plan.idempotency().key()).orElseThrow();
        return findByExecutionId(id).orElseThrow();
    }
    @Override public Optional<AdmissionRecord> find(String tenant, String workspace, String key) {
        return jdbc.query("select execution_id,tenant_id,workspace_id,actor_id,composition_id,composition_revision,plan_id,idempotency_key,request_hash,ownership_generation,state,quota_charged from platform_execution_admission where tenant_id=? and workspace_id=? and idempotency_key=?", rs -> rs.next() ? Optional.of(row(rs)) : Optional.empty(), tenant, workspace, key);
    }
    @Override public Optional<AdmissionRecord> findByExecutionId(String id) {
        return jdbc.query("select execution_id,tenant_id,workspace_id,actor_id,composition_id,composition_revision,plan_id,idempotency_key,request_hash,ownership_generation,state,quota_charged from platform_execution_admission where execution_id=?", rs -> rs.next() ? Optional.of(row(rs)) : Optional.empty(), id);
    }
    @Override public boolean transition(String id, long generation, String from, String to) {
        return jdbc.update("update platform_execution_admission set state=?, updated_at=? where execution_id=? and ownership_generation=? and state=?", to, Instant.now(), id, generation, from) == 1;
    }
    @Override public boolean claimQuotaCharge(String id) {
        return jdbc.update("update platform_execution_admission set quota_charged=true, updated_at=? where execution_id=? and quota_charged=false", Instant.now(), id) == 1;
    }
    @Override public void releaseQuotaCharge(String id) { jdbc.update("update platform_execution_admission set quota_charged=false, updated_at=? where execution_id=? and quota_charged=true", Instant.now(), id); }
    private static AdmissionRecord row(java.sql.ResultSet r) throws java.sql.SQLException { return new AdmissionRecord(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(6),r.getLong(7),r.getString(8),r.getString(9),r.getString(10),r.getLong(11),r.getString(12),r.getBoolean(13)); }
}
