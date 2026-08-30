package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Operational bootstrap boundary for trusted database observation and stable replay. */
public class StorageDatabaseBindingService {

    private final StorageDatabaseBindingObserver observer;
    private final TrustedStorageDatabaseBindingPolicy policy;
    private final StorageDatabaseBindingRepository repository;

    public StorageDatabaseBindingService(
            StorageDatabaseBindingObserver observer,
            TrustedStorageDatabaseBindingPolicy policy,
            StorageDatabaseBindingRepository repository) {
        this.observer = Objects.requireNonNull(observer, "observer");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Transactional
    public StorageDatabaseBinding observeAndRecord() {
        return repository.recordObservation(policy.bind(observer.observe()));
    }
}
