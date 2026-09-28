package com.example.platform.composition.app;

import com.example.platform.execution.admission.ProviderBoundExecutionPlan;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcCompositionAdmissionRepository implements CompositionAdmissionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcCompositionAdmissionRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Override public AdmissionRecord admit(ProviderBoundExecutionPlan plan) {
        String compositionId = plan.publishedRevision().subjectId(); long revision = plan.publishedRevision().revision();
        String id = CompositionExecutionIds.of(plan.scope().tenantId(), plan.scope().workspaceId(), plan.idempotency().key());
        final String facts;
        try { facts = json.writeValueAsString(plan); } catch (Exception e) { throw new IllegalStateException("provider-bound plan cannot be persisted", e); }
        int inserted = jdbc.update("""
            insert into platform_execution_admission
              (execution_id, tenant_id, workspace_id, actor_id, source_domain, composition_id,
               composition_revision, plan_id, idempotency_key, request_hash, ownership_generation,
               state, quota_units, quota_charged_units, quota_charged, quota_claimed, plan_fingerprint, plan_facts, quota_unit, created_at, updated_at)
            values (?,?,?,?,?,?,?,?,?,?,0,'ADMITTED',?,null,false,false,?,?::jsonb,?, ?,?)
            on conflict (tenant_id, workspace_id, idempotency_key) do nothing
            """, id, plan.scope().tenantId(), plan.scope().workspaceId(), plan.scope().actorId(),
                "composition", compositionId, revision, id, plan.idempotency().key(), plan.idempotency().requestHash(),
                plan.entitlementQuota().quotaUnits(), plan.planFingerprint().value(), facts,
                "quota-unit", Instant.now(), Instant.now());
        if (inserted == 0) return find(plan.scope().tenantId(), plan.scope().workspaceId(), plan.idempotency().key()).orElseThrow();
        return findByExecutionId(id).orElseThrow();
    }
    @Override public Optional<AdmissionRecord> find(String tenant, String workspace, String key) {
        return jdbc.query("select execution_id,tenant_id,workspace_id,actor_id,composition_id,composition_revision,plan_fingerprint,idempotency_key,request_hash,ownership_generation,state,quota_claimed,quota_charged,quota_charged_units,plan_facts from platform_execution_admission where tenant_id=? and workspace_id=? and idempotency_key=? for update", rs -> rs.next() ? Optional.of(row(rs)) : Optional.empty(), tenant, workspace, key);
    }
    @Override public Optional<AdmissionRecord> findByExecutionId(String id) {
        return jdbc.query("select execution_id,tenant_id,workspace_id,actor_id,composition_id,composition_revision,plan_fingerprint,idempotency_key,request_hash,ownership_generation,state,quota_claimed,quota_charged,quota_charged_units,plan_facts from platform_execution_admission where execution_id=?", rs -> rs.next() ? Optional.of(row(rs)) : Optional.empty(), id);
    }
    @Override public boolean transition(String id, long generation, String from, String to) {
        return jdbc.update("update platform_execution_admission set state=?, updated_at=? where execution_id=? and ownership_generation=? and state=?", to, Instant.now(), id, generation, from) == 1;
    }
    @Override public boolean claimQuotaCharge(String id) {
        return jdbc.update("update platform_execution_admission set quota_claimed=true, updated_at=? where execution_id=? and quota_claimed=false and quota_charged=false", Instant.now(), id) == 1;
    }
    @Override public void markQuotaCharged(String id, BigDecimal chargedUnits) { jdbc.update("update platform_execution_admission set quota_charged=true, quota_charged_units=?, updated_at=? where execution_id=? and quota_claimed=true and quota_charged=false", chargedUnits, Instant.now(), id); }
    @Override public void releaseQuotaCharge(String id) { jdbc.update("update platform_execution_admission set quota_charged=false, updated_at=? where execution_id=? and quota_charged=true", Instant.now(), id); }
    @Override public void deleteUncharged(String id) { jdbc.update("delete from platform_execution_admission where execution_id=? and quota_charged=false", id); }
    private AdmissionRecord row(java.sql.ResultSet r) throws java.sql.SQLException {
        try { return new AdmissionRecord(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6),r.getString(7),r.getString(8),r.getString(9),r.getLong(10),r.getString(11),r.getBoolean(12),r.getBoolean(13),r.getBigDecimal(14),r.getString(15) == null ? null : json.readValue(r.getString(15), ProviderBoundExecutionPlan.class)); }
        catch (Exception e) { throw new java.sql.SQLException("invalid persisted provider-bound plan", e); }
    }
}
