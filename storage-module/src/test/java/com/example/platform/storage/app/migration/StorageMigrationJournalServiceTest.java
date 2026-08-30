package com.example.platform.storage.app.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationResult;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageMigrationJournal;
import com.example.platform.storage.domain.migration.StorageMigrationJournal.JournalRecord;
import com.example.platform.storage.domain.migration.StorageMigrationJournal.JournalSeed;
import com.example.platform.storage.domain.migration.StorageMigrationJournal.State;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StorageMigrationJournalServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void deterministicSeedAndStableReplay() {
        JournalSeed firstSeed = seed(List.of("evidence-a"));
        JournalSeed secondSeed = seed(List.of("evidence-a"));
        assertEquals(firstSeed, secondSeed);

        InMemoryJournalRepository repository = new InMemoryJournalRepository();
        StorageMigrationJournalService service = service(repository);
        JournalRecord first = service.createOrLoad(binding(), firstSeed);
        JournalRecord replay = service.createOrLoad(binding(), secondSeed);

        assertEquals(first, replay);
        assertEquals(State.PENDING_CLASSIFICATION, first.state());
    }

    @Test
    void sameMigrationKeyWithDifferentSemanticFingerprintFailsClosed() {
        JournalSeed first = seed(List.of("evidence-a"));
        JournalSeed changed = seed(List.of("evidence-b"));
        assertEquals(first.migrationKey(), changed.migrationKey());
        assertNotEquals(first.semanticFingerprint(), changed.semanticFingerprint());

        InMemoryJournalRepository repository = new InMemoryJournalRepository();
        StorageMigrationJournalService service = service(repository);
        service.createOrLoad(binding(), first);
        assertThrows(StorageMigrationJournalConflictException.class,
                () -> service.createOrLoad(binding(), changed));
    }

    private static JournalSeed seed(List<String> semanticInputs) {
        return StorageMigrationJournal.seed(
                "binding-test", "artifact_replica", "artifact-1:replica-1",
                "original-value", "classifier-v1", "evidence-v1", semanticInputs);
    }

    private static StorageDatabaseBinding binding() {
        return StorageDatabaseBinding.testcontainers(
                "binding-test", "container:ephemeral", "test-run", "schema-1",
                "V1", "query-v1", NOW);
    }

    private static StorageMigrationJournalService service(InMemoryJournalRepository repository) {
        StorageMigrationObservationRepository observations = new StorageMigrationObservationRepository() {
            @Override
            public void recordDatabaseBinding(StorageDatabaseBinding binding) {}

            @Override
            public ClassificationResult saveOrLoadClassification(ClassificationResult classification) {
                return classification;
            }
        };
        return new StorageMigrationJournalService(
                repository, observations, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class InMemoryJournalRepository
            implements StorageMigrationJournalRepository {
        private final Map<String, JournalRecord> values = new HashMap<>();

        @Override
        public void lockMigrationKey(String migrationKey) {}

        @Override
        public Optional<JournalRecord> find(String migrationKey) {
            return Optional.ofNullable(values.get(migrationKey));
        }

        @Override
        public JournalRecord create(JournalSeed seed, Instant createdAt) {
            JournalRecord record = new JournalRecord(seed, State.PENDING_CLASSIFICATION, 0,
                    createdAt, createdAt);
            values.put(seed.migrationKey(), record);
            return record;
        }
    }
}
