package com.example.platform.compatibility.adapter;

import com.example.platform.compatibility.domain.*;

import java.util.List;
import java.util.Optional;

/**
 * SPI for migration adapters that transform versioned payloads between schema versions.
 */
public interface MigrationAdapter {

    /**
     * Unique key for this adapter.
     */
    String adapterKey();

    /**
     * Whether this adapter supports the given migration.
     */
    boolean supports(SchemaFamily schemaFamily, SchemaVersion sourceVersion, SchemaVersion targetVersion);

    /**
     * Create a migration plan for the given input and target version.
     */
    MigrationPlan plan(VersionedPayload input, SchemaVersion targetVersion);

    /**
     * Execute the migration.
     */
    VersionedPayload migrate(VersionedPayload input, MigrationPlan plan);

    /**
     * Validate the input payload.
     */
    List<MigrationError> validate(VersionedPayload input);

    /**
     * Perform a dry run without modifying the original.
     */
    MigrationResult dryRun(VersionedPayload input, SchemaVersion targetVersion);
}
