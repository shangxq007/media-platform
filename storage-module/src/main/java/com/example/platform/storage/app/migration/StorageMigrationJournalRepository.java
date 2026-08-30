package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.migration.StorageMigrationJournal.JournalRecord;
import com.example.platform.storage.domain.migration.StorageMigrationJournal.JournalSeed;
import java.time.Instant;
import java.util.Optional;

/** Persistence port for idempotent creation/loading of migration process state. */
public interface StorageMigrationJournalRepository {

    void lockMigrationKey(String migrationKey);

    Optional<JournalRecord> find(String migrationKey);

    JournalRecord create(JournalSeed seed, Instant createdAt);
}
