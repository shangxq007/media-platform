package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagDecision;
import com.example.platform.policy.featureflag.domain.FeatureFlagSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Map;

/** PostgreSQL-backed append-only snapshot store. */
@Repository
public final class JdbcFeatureFlagSnapshotStore implements FeatureFlagSnapshotStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcFeatureFlagSnapshotStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void persist(FeatureFlagSnapshot snapshot) {
        try {
            String json = mapper.writeValueAsString(snapshot.decisions());
            jdbc.update("""
                    insert into feature_flag_evaluation_snapshot
                      (snapshot_id, provider_revision, tenant_id, workspace_id, captured_at, decisions_json)
                    values (?, ?, ?, ?, ?, cast(? as jsonb))
                    on conflict (snapshot_id) do nothing
                    """, snapshot.snapshotId(), snapshot.providerRevision(), snapshot.tenantId(),
                    snapshot.workspaceId(), Timestamp.from(snapshot.capturedAt()), json);
            FeatureFlagSnapshot existing = load(snapshot.snapshotId());
            if (existing == null || !existing.snapshotId().equals(snapshot.snapshotId())
                    || !existing.providerRevision().equals(snapshot.providerRevision())
                    || !existing.tenantId().equals(snapshot.tenantId())
                    || !java.util.Objects.equals(existing.workspaceId(), snapshot.workspaceId())
                    || !existing.decisions().equals(snapshot.decisions())) {
                throw new IllegalStateException("snapshot id already exists with different content");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist feature flag snapshot", e);
        }
    }

    @Override
    public FeatureFlagSnapshot load(String snapshotId) {
        return jdbc.query("""
                select snapshot_id, provider_revision, tenant_id, workspace_id, captured_at, decisions_json::text
                from feature_flag_evaluation_snapshot where snapshot_id = ?
                """, rs -> {
            if (!rs.next()) return null;
            try {
                Map<String, FeatureFlagDecision> decisions = mapper.readValue(rs.getString("decisions_json"),
                        new TypeReference<>() {});
                return new FeatureFlagSnapshot(rs.getString("snapshot_id"), rs.getString("provider_revision"),
                        rs.getString("tenant_id"), rs.getString("workspace_id"),
                        rs.getTimestamp("captured_at").toInstant(), decisions);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to read feature flag snapshot", e);
            }
        }, snapshotId);
    }
}
