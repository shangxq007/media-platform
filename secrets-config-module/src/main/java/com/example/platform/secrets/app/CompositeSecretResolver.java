package com.example.platform.secrets.app;

import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.SecretProvider;
import com.example.platform.secrets.api.port.SecretResolver;
import com.example.platform.secrets.config.SecretsProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CompositeSecretResolver implements SecretResolver {

    private final List<SecretProvider> providers;
    private final SecretsProperties properties;

    public CompositeSecretResolver(List<SecretProvider> providers, SecretsProperties properties) {
        this.providers = providers;
        this.properties = properties;
    }

    @Override
    public Optional<String> resolve(SecretRef ref) {
        return providerFor(ref).resolveScalar(ref);
    }

    @Override
    public Map<String, String> resolveMap(SecretRef ref) {
        return providerFor(ref).resolveMap(ref);
    }

    @Override
    public String storeCredentialMap(String namespace, String logicalKey, Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            throw new IllegalArgumentException("credentials must not be empty");
        }
        if (!properties.getVault().isEnabled()) {
            throw new IllegalStateException("Vault is not enabled; cannot store credential map externally");
        }
        String path = properties.getVault().getPathPrefix() + "/" + namespace + "/" + logicalKey;
        SecretRef ref = SecretRef.vault(path);
        SecretProvider vault = providerFor(ref);
        if (!vault.canWrite(ref)) {
            throw new IllegalStateException("No writable secret provider for " + ref.encode());
        }
        vault.writeMap(ref, credentials);
        return ref.encode();
    }

    @Override
    public void deleteByRef(String encodedRef) {
        if (encodedRef == null || encodedRef.isBlank()) {
            return;
        }
        SecretRef ref = SecretRef.parse(encodedRef);
        SecretProvider provider = providerFor(ref);
        if (provider.canDelete(ref)) {
            provider.delete(ref);
        }
    }

    @Override
    public boolean isVaultEnabled() {
        return properties.getVault().isEnabled();
    }

    private SecretProvider providerFor(SecretRef ref) {
        return providers.stream()
                .filter(p -> p.supports(ref))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No secret provider for backend: " + ref.backend()));
    }
}
