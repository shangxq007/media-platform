package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageMigrationJournal.JournalRecord;
import com.example.platform.storage.domain.migration.StorageMigrationJournal.JournalSeed;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** M1 journal foundation; it creates only PENDING_CLASSIFICATION process state. */
@Service
public class StorageMigrationJournalService {

    private final StorageMigrationJournalRepository journalRepository;
    private final StorageMigrationObservationRepository observationRepository;
    private final Clock clock;

    public StorageMigrationJournalService(
            StorageMigrationJournalRepository journalRepository,
            StorageMigrationObservationRepository observationRepository,
            Clock clock) {
        this.journalRepository = Objects.requireNonNull(journalRepository, "journalRepository");
        this.observationRepository = Objects.requireNonNull(observationRepository, "observationRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public JournalRecord createOrLoad(StorageDatabaseBinding binding, JournalSeed seed) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(seed, "seed");
        if (!binding.bindingId().equals(seed.databaseBindingId())) {
            throw new IllegalArgumentException("journal seed must use the supplied database binding");
        }
        observationRepository.recordDatabaseBinding(binding);
        journalRepository.lockMigrationKey(seed.migrationKey());
        return journalRepository.find(seed.migrationKey())
                .map(existing -> replayOrFail(seed, existing))
                .orElseGet(() -> journalRepository.create(seed, clock.instant()));
    }

    private static JournalRecord replayOrFail(JournalSeed seed, JournalRecord existing) {
        if (!existing.seed().equals(seed)) {
            throw new StorageMigrationJournalConflictException(
                    "migration key was already used with a different semantic fingerprint or input");
        }
        return existing;
    }
}
