package com.example.platform.app.ai;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves LiteLLM API key: per-tenant virtual key when configured, else platform master key.
 * Tenant keys may be stored inline (MVP) or in Vault ({@code vault_ref}).
 */
@Service
public class TenantLitellmKeyService {

    private final TenantLitellmKeyRepository repository;
    private final TenantLitellmKeyCredentialService credentialService;

    @Value("${spring.ai.openai.api-key:}")
    private String platformMasterKey;

    @Value("${app.ai.providers.openai.tenant-virtual-keys-enabled:false}")
    private boolean tenantVirtualKeysEnabled;

    public TenantLitellmKeyService(
            TenantLitellmKeyRepository repository, TenantLitellmKeyCredentialService credentialService) {
        this.repository = repository;
        this.credentialService = credentialService;
    }

    public boolean isTenantVirtualKeysEnabled() {
        return tenantVirtualKeysEnabled;
    }

    public boolean isTenantKeysVaultBacked() {
        return credentialService.isVaultBackedMode();
    }

    public boolean isVaultAvailable() {
        return credentialService.isVaultAvailable();
    }

    public ResolvedLitellmKey resolveForTenant(String tenantId) {
        if (tenantVirtualKeysEnabled && tenantId != null && !tenantId.isBlank()) {
            Optional<TenantLitellmKeyRepository.TenantLitellmKeyRecord> record =
                    repository.findByTenantId(tenantId);
            if (record.isPresent() && record.get().enabled()) {
                Optional<String> key = credentialService.resolveVirtualKey(
                        record.get().virtualKey(), record.get().vaultRef());
                if (key.isPresent()) {
                    String source = record.get().vaultRef() != null && !record.get().vaultRef().isBlank()
                            ? "tenant-virtual-key-vault"
                            : "tenant-virtual-key";
                    return new ResolvedLitellmKey(key.get(), source, true);
                }
            }
        }
        return new ResolvedLitellmKey(
                platformMasterKey,
                tenantVirtualKeysEnabled ? "platform-master-key" : "platform-default",
                false);
    }

    public Optional<TenantLitellmKeyView> getView(String tenantId) {
        return repository.findByTenantId(tenantId).map(this::toView);
    }

    public TenantLitellmKeyView upsert(String tenantId, String virtualKey, String keyAlias, boolean enabled) {
        TenantLitellmKeyCredentialService.StoredLitellmKey stored =
                credentialService.persist(tenantId, virtualKey, null);
        repository.upsert(
                tenantId,
                stored.inlineVirtualKey(),
                stored.vaultRef(),
                keyAlias,
                enabled);
        return getView(tenantId).orElseThrow();
    }

    public void remove(String tenantId) {
        repository.findByTenantId(tenantId).ifPresent(r -> credentialService.revoke(r.vaultRef()));
        repository.delete(tenantId);
    }

    private TenantLitellmKeyView toView(TenantLitellmKeyRepository.TenantLitellmKeyRecord r) {
        boolean vaultStored = r.vaultRef() != null && !r.vaultRef().isBlank();
        String masked = vaultStored
                ? maskVaultRef(r.vaultRef())
                : maskKey(r.virtualKey());
        String storageBackend = vaultStored ? "vault" : "inline";
        return new TenantLitellmKeyView(
                r.tenantId(),
                masked,
                r.keyAlias(),
                r.enabled(),
                storageBackend,
                r.updatedAt() != null ? r.updatedAt().toString() : null);
    }

    static String maskKey(String key) {
        if (key == null || key.length() < 8) {
            return "****";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    static String maskVaultRef(String vaultRef) {
        if (vaultRef == null || vaultRef.isBlank()) {
            return "****";
        }
        int slash = vaultRef.lastIndexOf('/');
        if (slash >= 0 && slash < vaultRef.length() - 1) {
            return "vault:.../" + vaultRef.substring(slash + 1);
        }
        return vaultRef.length() > 24 ? vaultRef.substring(0, 12) + "..." : vaultRef;
    }

    public record ResolvedLitellmKey(String apiKey, String source, boolean tenantScoped) {}

    public record TenantLitellmKeyView(
            String tenantId,
            String maskedVirtualKey,
            String keyAlias,
            boolean enabled,
            String storageBackend,
            String updatedAt) {}
}
