package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.migration.StorageDatabaseBinding;

/** Persists one stable set of database facts per trusted binding identifier. */
public interface StorageDatabaseBindingRepository {

    StorageDatabaseBinding recordObservation(StorageDatabaseBinding binding);
}
