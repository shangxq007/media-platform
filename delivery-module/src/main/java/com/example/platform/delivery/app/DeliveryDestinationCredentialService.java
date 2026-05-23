package com.example.platform.delivery.app;

import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.SecretRefRegistryPort;
import com.example.platform.secrets.api.port.SecretResolver;
import com.example.platform.secrets.api.port.SecretsConfigPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Persists delivery destination credentials to Vault (preferred) or legacy {@code credential_json}.
 */
@Service
public class DeliveryDestinationCredentialService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SecretResolver secretResolver;
    private final SecretRefRegistryPort secretRefRegistry;
    private final SecretsConfigPort secretsConfig;

    public DeliveryDestinationCredentialService(
            SecretResolver secretResolver,
            SecretRefRegistryPort secretRefRegistry,
            SecretsConfigPort secretsConfig) {
        this.secretResolver = secretResolver;
        this.secretRefRegistry = secretRefRegistry;
        this.secretsConfig = secretsConfig;
    }

    public StoredCredentials persist(
            String tenantId,
            String destinationId,
            String explicitCredentialRef,
            Map<String, String> inlineCredentials) {
        if (explicitCredentialRef != null && !explicitCredentialRef.isBlank()) {
            return new StoredCredentials(explicitCredentialRef.trim(), null);
        }
        if (inlineCredentials == null || inlineCredentials.isEmpty()) {
            return new StoredCredentials(null, null);
        }
        if (secretsConfig.vaultEnabled()) {
            String logicalKey = "tenants/" + tenantId + "/destinations/" + destinationId;
            String encodedRef = secretResolver.storeCredentialMap("delivery", logicalKey, inlineCredentials);
            secretRefRegistry.register("delivery", destinationId, "vault", encodedRef);
            return new StoredCredentials(encodedRef, null);
        }
        if (secretsConfig.inlineCredentialsEnabled()) {
            return new StoredCredentials(null, toJson(inlineCredentials));
        }
        throw new IllegalStateException(
                "Inline credentials are disabled; enable Vault (app.secrets.vault.enabled=true) "
                        + "or set app.secrets.inline-credentials-enabled=true for development");
    }

    /**
     * Removes Vault secret when destination is deleted (best-effort).
     */
    public void revoke(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return;
        }
        if (!secretsConfig.vaultEnabled()) {
            return;
        }
        try {
            SecretRef ref = SecretRef.parse(credentialRef);
            if (SecretRef.BACKEND_VAULT.equals(ref.backend())) {
                secretResolver.deleteByRef(credentialRef);
            }
        } catch (Exception e) {
            // non-fatal on delete
        }
    }

    public record StoredCredentials(String credentialRef, String credentialJson) {}

    private static String toJson(Map<String, String> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid credentials map", e);
        }
    }
}
