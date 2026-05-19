package com.example.platform.compatibility.service;

import com.example.platform.compatibility.adapter.*;
import com.example.platform.compatibility.domain.*;
import com.example.platform.compatibility.policy.MigrationPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class MigrationService {
    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private final JsonPatchMigrationAdapter jsonPatchAdapter;
    private final JavaMigrationAdapter javaMigrationAdapter;
    private final ExtensionScriptMigrationAdapter extensionScriptAdapter;
    private final MigrationPolicyService policyService;

    public MigrationService(JsonPatchMigrationAdapter jsonPatchAdapter,
                            JavaMigrationAdapter javaMigrationAdapter,
                            ExtensionScriptMigrationAdapter extensionScriptAdapter,
                            MigrationPolicyService policyService) {
        this.jsonPatchAdapter = jsonPatchAdapter;
        this.javaMigrationAdapter = javaMigrationAdapter;
        this.extensionScriptAdapter = extensionScriptAdapter;
        this.policyService = policyService;
    }

    public MigrationResult dryRun(VersionedPayload input, SchemaVersion targetVersion,
                                   MigrationAuditContext auditCtx) {
        log.info("MigrationService: dry-run for {} {} -> {}", input.schemaFamily(), input.schemaVersion(), targetVersion);

        MigrationPolicyService.MigrationPolicyDecision policy = policyService.decide(
                new MigrationPolicyService.MigrationPolicyContext(
                        input.schemaFamily(), input.schemaVersion(), targetVersion,
                        auditCtx.tenantId(), "PRO", false,
                        input.payload().size(), false, false, false
                ));

        if (!policy.autoMigratable()) {
            return new MigrationResult(
                    auditCtx.migrationRunId(), "none", MigrationStatus.SKIPPED,
                    input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                    null, List.of(), List.of("Policy decision: " + policy.reason())
            );
        }

        MigrationAdapter adapter = selectAdapter(policy.selectedAdapter());
        return adapter.dryRun(input, targetVersion);
    }

    public MigrationResult migrate(VersionedPayload input, SchemaVersion targetVersion,
                                    MigrationAuditContext auditCtx) {
        log.info("MigrationService: migrate for {} {} -> {}", input.schemaFamily(), input.schemaVersion(), targetVersion);

        try {
            MigrationPolicyService.MigrationPolicyDecision policy = policyService.decide(
                    new MigrationPolicyService.MigrationPolicyContext(
                            input.schemaFamily(), input.schemaVersion(), targetVersion,
                            auditCtx.tenantId(), "PRO", false,
                            input.payload().size(), false, false, false
                    ));

            if (!policy.autoMigratable()) {
                return new MigrationResult(
                        auditCtx.migrationRunId(), "none", MigrationStatus.SKIPPED,
                        input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                        null, List.of(), List.of("Skipped by policy: " + policy.reason())
                );
            }

            MigrationAdapter adapter = selectAdapter(policy.selectedAdapter());
            MigrationPlan plan = adapter.plan(input, targetVersion);
            VersionedPayload migrated = adapter.migrate(input, plan);

            return new MigrationResult(
                    auditCtx.migrationRunId(), plan.migrationPlanId(), MigrationStatus.COMPLETED,
                    input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                    migrated, List.of(), List.of()
            );
        } catch (Exception e) {
            log.error("MigrationService: migration failed", e);
            return new MigrationResult(
                    auditCtx.migrationRunId(), "none", MigrationStatus.FAILED,
                    input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                    null, List.of(new MigrationError("MIGRATION_FAILED", e.getMessage(), null, false)),
                    List.of()
            );
        }
    }

    private MigrationAdapter selectAdapter(String adapterKey) {
        return switch (adapterKey) {
            case "java-migration" -> javaMigrationAdapter;
            case "extension-script" -> extensionScriptAdapter;
            default -> jsonPatchAdapter;
        };
    }
}
