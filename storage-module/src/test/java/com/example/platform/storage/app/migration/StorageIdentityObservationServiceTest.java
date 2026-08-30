package com.example.platform.storage.app.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationEvidence;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationInput;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationResult;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageIdentityObservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void persistsOnlyTheObserverProducedBinding() {
        StorageDatabaseBinding observed = observedBinding("binding-observed");
        CapturingRepository repository = new CapturingRepository();
        StorageIdentityObservationService service = new StorageIdentityObservationService(
                new PersistedStorageIdentityClassifier(), expectation -> observed,
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        ClassificationResult result = service.observe(
                expectation("binding-observed"), input("binding-observed"));

        assertSame(observed, repository.binding);
        assertEquals("binding-observed", result.databaseBindingId());
    }

    @Test
    void rejectsClassificationThatNamesAnythingOtherThanObservedBinding() {
        CapturingRepository repository = new CapturingRepository();
        StorageIdentityObservationService service = new StorageIdentityObservationService(
                new PersistedStorageIdentityClassifier(),
                expectation -> observedBinding("observer-selected"),
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class,
                () -> service.observe(expectation("declared"), input("caller-selected")));
        assertEquals(null, repository.binding);
    }

    private static StorageDatabaseBindingExpectation expectation(String bindingId) {
        return new StorageDatabaseBindingExpectation(
                bindingId, DatabaseKind.EXPLICIT, "deployment-a", DeploymentEnvironment.DEV,
                "media", "public", "V1", "query-v1", "evidence:binding-a", true);
    }

    private static StorageDatabaseBinding observedBinding(String bindingId) {
        return StorageDatabaseBinding.explicitCanonical(
                bindingId, "postgresql:sha256:" + "a".repeat(64), "deployment-a",
                DeploymentEnvironment.DEV, "public", "V1", "query-v1",
                "evidence:binding-a", NOW);
    }

    private static ClassificationInput input(String bindingId) {
        return new ClassificationInput(
                bindingId, "storage_object", "source-1", "persisted-value",
                "classifier-v1", "evidence-v1",
                new ClassificationEvidence(false, false, false, false, false, false, List.of()));
    }

    private static final class CapturingRepository implements StorageMigrationObservationRepository {
        private StorageDatabaseBinding binding;

        @Override
        public void recordDatabaseBinding(StorageDatabaseBinding binding) {
            this.binding = binding;
        }

        @Override
        public ClassificationResult saveOrLoadClassification(ClassificationResult classification) {
            return classification;
        }
    }
}
