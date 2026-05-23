package com.example.platform.app.ai;

import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.SecretRefRegistryPort;
import com.example.platform.secrets.api.port.SecretResolver;
import com.example.platform.secrets.api.port.SecretsConfigPort;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Persists tenant LiteLLM virtual keys in Vault (preferred) or inline DB column (dev/MVP).
 */
@Service
public class TenantLitellmKeyCredentialService {

    private static final String CREDENTIAL_FIELD = "virtualKey";

    private final SecretResolver secretResolver;
    private final SecretRefRegistryPort secretRefRegistry;
    private final SecretsConfigPort secretsConfig;

    @Value("${app.ai.providers.openai.tenant-keys-vault-backed:false}")
    private boolean tenantKeysVaultBacked;

    public TenantLitellmKeyCredentialService(
            SecretResolver secretResolver,
            SecretRefRegistryPort secretRefRegistry,
            SecretsConfigPort secretsConfig) {
        this.secretResolver = secretResolver;
        this.secretRefRegistry = secretRefRegistry;
        this.secretsConfig = secretsConfig;
    }

    public boolean isVaultBackedMode() {
        return tenantKeysVaultBacked;
    }

    public boolean isVaultAvailable() {
        return secretsConfig.vaultEnabled();
    }

    public StoredLitellmKey persist(String tenantId, String virtualKey, String explicitVaultRef) {
        if (explicitVaultRef != null && !explicitVaultRef.isBlank()) {
            return new StoredLitellmKey(null, explicitVaultRef.trim(), StorageBackend.VAULT);
        }
        if (tenantKeysVaultBacked) {
            if (!secretsConfig.vaultEnabled()) {
                throw new IllegalStateException(
                        "tenant-keys-vault-backed is enabled but Vault is not "
                                + "(set app.secrets.vault.enabled=true)");
            }
            String logicalKey = "tenants/" + tenantId + "/litellm";
            String encodedRef = secretResolver.storeCredentialMap(
                    "ai-litellm", logicalKey, Map.of(CREDENTIAL_FIELD, virtualKey));
            secretRefRegistry.register("ai-litellm", tenantId, "vault", encodedRef);
            return new StoredLitellmKey(null, encodedRef, StorageBackend.VAULT);
        }
        if (secretsConfig.inlineCredentialsEnabled()) {
            return new StoredLitellmKey(virtualKey, null, StorageBackend.INLINE);
        }
        throw new IllegalStateException(
                "Inline LiteLLM keys are disabled; enable Vault-backed mode "
                        + "(app.ai.providers.openai.tenant-keys-vault-backed=true) "
                        + "or app.secrets.inline-credentials-enabled=true for development");
    }

    public Optional<String> resolveVirtualKey(String inlineKey, String vaultRef) {
        if (vaultRef != null && !vaultRef.isBlank()) {
            try {
                SecretRef ref = SecretRef.parse(vaultRef);
                if (ref.field() != null && !ref.field().isBlank()) {
                    return secretResolver.resolve(ref);
                }
                Map<String, String> map = secretResolver.resolveMap(ref);
                String fromMap = map.get(CREDENTIAL_FIELD);
                if (fromMap != null && !fromMap.isBlank()) {
                    return Optional.of(fromMap);
                }
                return secretResolver.resolve(SecretRef.vaultField(ref.path(), CREDENTIAL_FIELD));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to resolve LiteLLM key from Vault: " + vaultRef, e);
            }
        }
        if (inlineKey != null && !inlineKey.isBlank()) {
            return Optional.of(inlineKey);
        }
        return Optional.empty();
    }

    public void revoke(String vaultRef) {
        if (vaultRef == null || vaultRef.isBlank() || !secretsConfig.vaultEnabled()) {
            return;
        }
        try {
            SecretRef ref = SecretRef.parse(vaultRef);
            if (SecretRef.BACKEND_VAULT.equals(ref.backend())) {
                secretResolver.deleteByRef(vaultRef);
            }
        } catch (Exception ignored) {
            // best-effort on delete
        }
    }

    public enum StorageBackend {
        INLINE,
        VAULT
    }

    public record StoredLitellmKey(String inlineVirtualKey, String vaultRef, StorageBackend storageBackend) {}
}
