package com.example.platform.compatibility.adapter;

import com.example.platform.compatibility.domain.*;
import com.example.platform.extension.domain.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Adapter that executes migration scripts through extension-module's controlled execution layer.
 *
 * <p>Scripts must be whitelisted in extension-module CLI tools configuration.
 * No direct ProcessBuilder usage — all execution goes through ToolRunner.</p>
 */
@Component
public class ExtensionScriptMigrationAdapter implements MigrationAdapter {
    private static final Logger log = LoggerFactory.getLogger(ExtensionScriptMigrationAdapter.class);

    @Override
    public String adapterKey() {
        return "extension-script";
    }

    @Override
    public boolean supports(SchemaFamily schemaFamily, SchemaVersion sourceVersion, SchemaVersion targetVersion) {
        return false; // Script migrations not enabled by default
    }

    @Override
    public MigrationPlan plan(VersionedPayload input, SchemaVersion targetVersion) {
        return new MigrationPlan(
                UUID.randomUUID().toString(),
                input.schemaFamily(),
                input.schemaVersion(),
                targetVersion,
                List.of(new MigrationStep(
                        "script-migrate",
                        "Script migration via extension-module",
                        adapterKey(),
                        Map.of("timeout", 30, "workDir", "/tmp/migration")
                )),
                false, "HIGH"
        );
    }

    @Override
    public VersionedPayload migrate(VersionedPayload input, MigrationPlan plan) {
        log.info("ExtensionScriptMigrationAdapter: would execute whitelisted script for {} {} -> {}",
                input.schemaFamily(), input.schemaVersion(), plan.targetVersion());
        // In production, this would call extension-module's ToolRunner with whitelisted script
        // For now, delegate to JsonPatchMigrationAdapter
        return new JsonPatchMigrationAdapter().migrate(input, plan);
    }

    @Override
    public List<MigrationError> validate(VersionedPayload input) {
        List<MigrationError> errors = new ArrayList<>();
        if (input.payload() == null || input.payload().isEmpty()) {
            errors.add(new MigrationError("EMPTY_PAYLOAD", "Payload is empty", null, false));
        }
        return errors;
    }

    @Override
    public MigrationResult dryRun(VersionedPayload input, SchemaVersion targetVersion) {
        MigrationPlan plan = plan(input, targetVersion);
        try {
            VersionedPayload migrated = migrate(input, plan);
            return new MigrationResult(
                    UUID.randomUUID().toString(), plan.migrationPlanId(), MigrationStatus.COMPLETED,
                    input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                    migrated, List.of(), List.of("DRY_RUN via extension-script adapter")
            );
        } catch (Exception e) {
            return new MigrationResult(
                    UUID.randomUUID().toString(), plan.migrationPlanId(), MigrationStatus.FAILED,
                    input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                    null, List.of(new MigrationError("SCRIPT_MIGRATION_FAILED", e.getMessage(), null, false)),
                    List.of()
            );
        }
    }
}
