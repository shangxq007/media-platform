package com.example.platform.app.ai;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migrates inline {@code virtual_key} values to Vault and clears plaintext column.
 */
@Service
public class TenantLitellmKeyVaultMigrationService {

    private static final Logger log = LoggerFactory.getLogger(TenantLitellmKeyVaultMigrationService.class);

    private final TenantLitellmKeyRepository repository;
    private final TenantLitellmKeyCredentialService credentialService;

    public TenantLitellmKeyVaultMigrationService(
            TenantLitellmKeyRepository repository, TenantLitellmKeyCredentialService credentialService) {
        this.repository = repository;
        this.credentialService = credentialService;
    }

    @Transactional
    public MigrationReport migrateInlineKeysToVault(boolean dryRun) {
        if (!credentialService.isVaultBackedMode()) {
            throw new IllegalStateException(
                    "Enable app.ai.providers.openai.tenant-keys-vault-backed=true before migration");
        }
        if (!credentialService.isVaultAvailable()) {
            throw new IllegalStateException("Vault is not enabled (app.secrets.vault.enabled=true required)");
        }

        List<String> migrated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (TenantLitellmKeyRepository.TenantLitellmKeyRecord record : repository.findAllInlineKeys()) {
            String tenantId = record.tenantId();
            if (record.vaultRef() != null && !record.vaultRef().isBlank()) {
                skipped.add(tenantId + ":already-vault");
                continue;
            }
            if (record.virtualKey() == null || record.virtualKey().isBlank()) {
                skipped.add(tenantId + ":no-inline-key");
                continue;
            }
            try {
                if (!dryRun) {
                    TenantLitellmKeyCredentialService.StoredLitellmKey stored =
                            credentialService.persist(tenantId, record.virtualKey(), null);
                    repository.upsert(
                            tenantId,
                            stored.inlineVirtualKey(),
                            stored.vaultRef(),
                            record.keyAlias(),
                            record.enabled());
                }
                migrated.add(tenantId);
            } catch (Exception e) {
                log.warn("LiteLLM key migration failed for {}: {}", tenantId, e.getMessage());
                failed.add(tenantId + ":" + e.getMessage());
            }
        }

        return new MigrationReport(dryRun, migrated.size(), skipped.size(), failed.size(), migrated, skipped, failed);
    }

    public record MigrationReport(
            boolean dryRun,
            int migratedCount,
            int skippedCount,
            int failedCount,
            List<String> migratedTenantIds,
            List<String> skipped,
            List<String> failed) {}
}
