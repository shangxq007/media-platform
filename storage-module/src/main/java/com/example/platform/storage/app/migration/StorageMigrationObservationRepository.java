package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationResult;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;

/** Persistence port for bound database identity and observe-only classification evidence. */
public interface StorageMigrationObservationRepository {

    void recordDatabaseBinding(StorageDatabaseBinding binding);

    ClassificationResult saveOrLoadClassification(ClassificationResult classification);
}
