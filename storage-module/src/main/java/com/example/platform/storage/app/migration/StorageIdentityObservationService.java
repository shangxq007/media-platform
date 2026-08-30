package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationInput;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationResult;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Observe-only M1 boundary. It records evidence and never mutates a source row. */
@Service
public class StorageIdentityObservationService {

    private final PersistedStorageIdentityClassifier classifier;
    private final StorageDatabaseBindingObserver bindingObserver;
    private final StorageMigrationObservationRepository repository;
    private final Clock clock;

    public StorageIdentityObservationService(
            PersistedStorageIdentityClassifier classifier,
            StorageDatabaseBindingObserver bindingObserver,
            StorageMigrationObservationRepository repository,
            Clock clock) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.bindingObserver = Objects.requireNonNull(bindingObserver, "bindingObserver");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public ClassificationResult observe(
            StorageDatabaseBindingExpectation expectation,
            ClassificationInput input) {
        Objects.requireNonNull(expectation, "expectation");
        Objects.requireNonNull(input, "input");
        StorageDatabaseBinding binding = bindingObserver.observe(expectation);
        if (!binding.bindingId().equals(input.databaseBindingId())) {
            throw new IllegalArgumentException(
                    "classification input must use the observer-produced database binding");
        }
        repository.recordDatabaseBinding(binding);
        ClassificationResult classified = classifier.classify(input, clock.instant());
        return repository.saveOrLoadClassification(classified);
    }
}
