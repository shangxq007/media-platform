package com.example.platform.storage.infrastructure.migration;

import com.example.platform.storage.app.migration.StorageDatabaseBindingRepository;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Fail-closed stable binding replay with monotonic last-observation updates. */
@Repository
public class JdbcStorageDatabaseBindingRepository implements StorageDatabaseBindingRepository {

    private final JdbcTemplate jdbc;

    public JdbcStorageDatabaseBindingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StorageDatabaseBinding recordObservation(StorageDatabaseBinding binding) {
        OffsetDateTime firstSeen = utc(binding.firstSeenAt());
        OffsetDateTime lastObserved = utc(binding.lastObservedAt());
        jdbc.update("""
                insert into storage_database_binding (
                    binding_id, database_kind, database_oid, database_name,
                    database_identity, deployment_identity, environment_identity,
                    schema_identity, schema_version, query_evidence_version,
                    binding_evidence_ref, is_canonical, first_seen_at, last_observed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (binding_id) do nothing
                """,
                binding.bindingId(), binding.databaseKind().name(), binding.databaseOid(),
                binding.databaseName(), binding.databaseIdentity(), binding.deploymentIdentity(),
                binding.environmentIdentity().name(), binding.schemaIdentity(),
                binding.schemaVersion(), binding.queryEvidenceVersion(),
                binding.bindingEvidenceRef(), binding.canonical(), firstSeen, lastObserved);

        StorageDatabaseBinding persisted = load(binding.bindingId());
        if (!persisted.hasSameStableFacts(binding)) {
            throw new IllegalStateException(
                    "database binding ID was already used with different stable facts");
        }
        if (binding.lastObservedAt().isAfter(persisted.lastObservedAt())) {
            jdbc.update("""
                    update storage_database_binding set last_observed_at = ?
                     where binding_id = ? and last_observed_at < ?
                    """, lastObserved, binding.bindingId(), lastObserved);
        }
        return load(binding.bindingId());
    }

    private StorageDatabaseBinding load(String bindingId) {
        return jdbc.queryForObject("""
                select binding_id, database_kind, database_oid, database_name,
                       database_identity, deployment_identity, environment_identity,
                       schema_identity, schema_version, query_evidence_version,
                       binding_evidence_ref, is_canonical, first_seen_at, last_observed_at
                  from storage_database_binding where binding_id = ?
                """, (rs, rowNumber) -> new StorageDatabaseBinding(
                rs.getString("binding_id"),
                DatabaseKind.valueOf(rs.getString("database_kind")),
                rs.getLong("database_oid"),
                rs.getString("database_name"),
                rs.getString("database_identity"),
                rs.getString("deployment_identity"),
                DeploymentEnvironment.valueOf(rs.getString("environment_identity")),
                rs.getString("schema_identity"),
                rs.getString("schema_version"),
                rs.getString("query_evidence_version"),
                rs.getString("binding_evidence_ref"),
                rs.getBoolean("is_canonical"),
                rs.getObject("first_seen_at", OffsetDateTime.class).toInstant(),
                rs.getObject("last_observed_at", OffsetDateTime.class).toInstant()),
                bindingId);
    }

    private static OffsetDateTime utc(java.time.Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
