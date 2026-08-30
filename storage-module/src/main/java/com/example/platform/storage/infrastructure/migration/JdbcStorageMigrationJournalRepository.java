package com.example.platform.storage.infrastructure.migration;

import com.example.platform.storage.app.migration.StorageMigrationJournalRepository;
import com.example.platform.storage.domain.migration.StorageMigrationJournal.JournalRecord;
import com.example.platform.storage.domain.migration.StorageMigrationJournal.JournalSeed;
import com.example.platform.storage.domain.migration.StorageMigrationJournal.State;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL persistence for inactive, restartable migration process state. */
@Repository
public class JdbcStorageMigrationJournalRepository
        implements StorageMigrationJournalRepository {

    private final JdbcTemplate jdbc;

    public JdbcStorageMigrationJournalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockMigrationKey(String migrationKey) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, 1))")) {
                statement.setString(1, migrationKey);
                statement.execute();
            }
            return null;
        });
    }

    @Override
    public Optional<JournalRecord> find(String migrationKey) {
        List<JournalRecord> records = jdbc.query("""
                select migration_key, semantic_fingerprint, database_binding_id,
                       source_table, source_primary_identity, expected_original_value,
                       classifier_version, evidence_version, journal_state, version,
                       created_at, updated_at
                  from storage_identity_migration_journal
                 where migration_key = ?
                """, (rs, rowNumber) -> {
            JournalSeed seed = new JournalSeed(
                    rs.getString("migration_key"),
                    rs.getString("semantic_fingerprint"),
                    rs.getString("database_binding_id"),
                    rs.getString("source_table"),
                    rs.getString("source_primary_identity"),
                    rs.getString("expected_original_value"),
                    rs.getString("classifier_version"),
                    rs.getString("evidence_version"));
            return new JournalRecord(
                    seed,
                    State.valueOf(rs.getString("journal_state")),
                    rs.getLong("version"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("updated_at", OffsetDateTime.class).toInstant());
        }, migrationKey);
        return records.stream().findFirst();
    }

    @Override
    public JournalRecord create(JournalSeed seed, Instant createdAt) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC);
        jdbc.update("""
                insert into storage_identity_migration_journal (
                    migration_key, semantic_fingerprint, database_binding_id, source_table,
                    source_primary_identity, expected_original_value, classifier_version,
                    evidence_version, journal_state, reconciliation_status,
                    source_reference_switch_status, version, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_CLASSIFICATION',
                          'NOT_STARTED', 'NOT_STARTED', 0, ?, ?)
                """,
                seed.migrationKey(), seed.semanticFingerprint(), seed.databaseBindingId(),
                seed.sourceTable(), seed.sourcePrimaryIdentity(), seed.expectedOriginalValue(),
                seed.classifierVersion(), seed.evidenceVersion(), timestamp, timestamp);
        return new JournalRecord(seed, State.PENDING_CLASSIFICATION, 0, createdAt, createdAt);
    }
}
