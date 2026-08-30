package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.migration.StorageDatabaseBinding;

/** Observes the database connection in use and verifies it against declared expectations. */
public interface StorageDatabaseBindingObserver {

    StorageDatabaseBinding observe(StorageDatabaseBindingExpectation expectation);
}
