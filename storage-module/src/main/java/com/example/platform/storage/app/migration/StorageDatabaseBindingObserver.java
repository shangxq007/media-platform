package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.migration.StorageDatabaseObservation;

/** Reports connected database facts and has no authority to declare them canonical. */
public interface StorageDatabaseBindingObserver {

    StorageDatabaseObservation observe();
}
