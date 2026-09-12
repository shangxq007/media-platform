package com.example.platform.delivery.app;

import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.SecretRefRegistryPort;
import com.example.platform.secrets.api.port.SecretResolver;
import com.example.platform.secrets.api.port.SecretsConfigPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
            var ref = SecretRef.parse(explicitCredentialRef.trim());
            String scope = "/delivery/tenants/" + tenantId + "/destinations/";
            if (!SecretRef.BACKEND_VAULT.equals(ref.backend()) || !ref.path().contains(scope)
                    || ref.path().contains("..") || ref.path().contains("%") || ref.path().contains("\\")
                    || (ref.path().contains("/versions/") && !ref.path().contains(scope + destinationId + "/versions/"))
                    || ref.field() != null) {
                throw new IllegalArgumentException("Credential reference is outside the authorized Delivery tenant scope");
            }
            return new StoredCredentials(explicitCredentialRef.trim(), null);
        }
        if (inlineCredentials == null || inlineCredentials.isEmpty()) {
            return new StoredCredentials(null, null);
        }
        if (secretsConfig.vaultEnabled()) {
            requireTransaction();
            // A fresh location never overwrites the credential still referenced by a committed destination.
            String logicalKey = "tenants/" + tenantId + "/destinations/" + destinationId
                    + "/versions/" + java.util.UUID.randomUUID();
            String encodedRef = secretResolver.storeCredentialMap("delivery", logicalKey, inlineCredentials);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) revoke(encodedRef);
                }
            });
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
     * Removes a Delivery-managed version. Shared/operator-provisioned references are not owned here.
     */
    private void revoke(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return;
        }
        SecretRef ref = SecretRef.parse(credentialRef);
        if (SecretRef.BACKEND_VAULT.equals(ref.backend()) && ref.path().contains("/delivery/tenants/")
                && ref.path().contains("/versions/")) {
            if (!secretsConfig.vaultEnabled()) {
                throw new IllegalStateException("Delivery credential cleanup requires Vault");
            }
            secretResolver.deleteByRef(credentialRef);
        }
    }

    public void revokeAfterCommit(String tenantId, String destinationId, String credentialRef) {
        requireTransaction();
        if (credentialRef == null || !SecretRef.parse(credentialRef).path().contains(
                "/delivery/tenants/" + tenantId + "/destinations/" + destinationId + "/versions/")) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { revoke(credentialRef); }
        });
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Delivery credential changes require an application transaction");
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
