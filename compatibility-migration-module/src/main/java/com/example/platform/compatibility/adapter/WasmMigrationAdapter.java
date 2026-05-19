package com.example.platform.compatibility.adapter;

import com.example.platform.compatibility.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Placeholder for future Wasm-based migration adapter.
 *
 * <p>Not enabled by default. Requires sandbox-runtime-module.</p>
 */
@Component
public class WasmMigrationAdapter implements MigrationAdapter {
    private static final Logger log = LoggerFactory.getLogger(WasmMigrationAdapter.class);

    @Override
    public String adapterKey() {
        return "wasm-migration";
    }

    @Override
    public boolean supports(SchemaFamily schemaFamily, SchemaVersion sourceVersion, SchemaVersion targetVersion) {
        return false; // Not enabled by default
    }

    @Override
    public MigrationPlan plan(VersionedPayload input, SchemaVersion targetVersion) {
        throw new UnsupportedOperationException("WasmMigrationAdapter is not enabled");
    }

    @Override
    public VersionedPayload migrate(VersionedPayload input, MigrationPlan plan) {
        throw new UnsupportedOperationException("WasmMigrationAdapter is not enabled");
    }

    @Override
    public List<MigrationError> validate(VersionedPayload input) {
        return List.of(new MigrationError("NOT_ENABLED", "WasmMigrationAdapter is not enabled", null, false));
    }

    @Override
    public MigrationResult dryRun(VersionedPayload input, SchemaVersion targetVersion) {
        return new MigrationResult(
                UUID.randomUUID().toString(), "none", MigrationStatus.SKIPPED,
                input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                null, List.of(), List.of("WasmMigrationAdapter not enabled")
        );
    }
}
