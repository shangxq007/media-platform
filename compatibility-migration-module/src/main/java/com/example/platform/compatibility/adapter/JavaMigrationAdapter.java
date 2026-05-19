package com.example.platform.compatibility.adapter;

import com.example.platform.compatibility.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter for project-internal Java migration handlers.
 */
@Component
public class JavaMigrationAdapter implements MigrationAdapter {
    private static final Logger log = LoggerFactory.getLogger(JavaMigrationAdapter.class);

    @Override
    public String adapterKey() {
        return "java-migration";
    }

    @Override
    public boolean supports(SchemaFamily schemaFamily, SchemaVersion sourceVersion, SchemaVersion targetVersion) {
        return sourceVersion.isBefore(targetVersion);
    }

    @Override
    public MigrationPlan plan(VersionedPayload input, SchemaVersion targetVersion) {
        return new MigrationPlan(
                UUID.randomUUID().toString(),
                input.schemaFamily(),
                input.schemaVersion(),
                targetVersion,
                List.of(new MigrationStep(
                        "java-migrate-" + input.schemaVersion() + "-to-" + targetVersion,
                        "Java migration handler",
                        adapterKey(),
                        Map.of("handlerClass", "auto")
                )),
                true, "MEDIUM"
        );
    }

    @Override
    public VersionedPayload migrate(VersionedPayload input, MigrationPlan plan) {
        // Delegates to JsonPatchMigrationAdapter for known transformations
        return new JsonPatchMigrationAdapter().migrate(input, plan);
    }

    @Override
    public List<MigrationError> validate(VersionedPayload input) {
        return new JsonPatchMigrationAdapter().validate(input);
    }

    @Override
    public MigrationResult dryRun(VersionedPayload input, SchemaVersion targetVersion) {
        return new JsonPatchMigrationAdapter().dryRun(input, targetVersion);
    }
}
