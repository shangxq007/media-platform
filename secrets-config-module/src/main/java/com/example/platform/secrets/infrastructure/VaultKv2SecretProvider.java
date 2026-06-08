package com.example.platform.secrets.infrastructure;

import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.SecretProvider;
import com.example.platform.secrets.config.SecretsProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.Versioned;

@Component
@ConditionalOnBean(VaultTemplate.class)
public class VaultKv2SecretProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultKv2SecretProvider.class);

    private final VaultTemplate vaultTemplate;
    private final String kvMount;

    public VaultKv2SecretProvider(VaultTemplate vaultTemplate, SecretsProperties properties) {
        this.vaultTemplate = vaultTemplate;
        this.kvMount = properties.getVault().getKvMount();
    }

    @Override
    public String backend() {
        return SecretRef.BACKEND_VAULT;
    }

    @Override
    public boolean supports(SecretRef ref) {
        return SecretRef.BACKEND_VAULT.equals(ref.backend());
    }

    @Override
    public Optional<String> resolveScalar(SecretRef ref) {
        Map<String, String> map = resolveMap(ref);
        if (ref.field() != null && !ref.field().isBlank()) {
            return Optional.ofNullable(map.get(ref.field()));
        }
        if (map.size() == 1) {
            return Optional.of(map.values().iterator().next());
        }
        return Optional.empty();
    }

    @Override
    public Map<String, String> resolveMap(SecretRef ref) {
        Versioned<Map<String, Object>> versioned = vaultTemplate
                .opsForVersionedKeyValue(kvMount)
                .get(normalizePath(ref.path()));
        if (versioned == null || versioned.getData() == null) {
            return Map.of();
        }
        Map<String, Object> data = versioned.getData();
        if (ref.field() != null && !ref.field().isBlank()) {
            Object value = data.get(ref.field());
            return value != null ? Map.of(ref.field(), String.valueOf(value)) : Map.of();
        }
        return data.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() != null ? String.valueOf(e.getValue()) : "",
                        (a, b) -> b,
                        LinkedHashMap::new));
    }

    @Override
    public boolean canWrite(SecretRef ref) {
        return supports(ref);
    }

    @Override
    public void writeMap(SecretRef ref, Map<String, String> values) {
        Map<String, Object> payload = new LinkedHashMap<>(values);
        vaultTemplate.opsForVersionedKeyValue(kvMount).put(normalizePath(ref.path()), payload);
        log.debug("Vault KV put path={}", ref.path());
    }

    @Override
    public boolean canDelete(SecretRef ref) {
        return supports(ref);
    }

    @Override
    public void delete(SecretRef ref) {
        String path = normalizePath(ref.path());
        try {
            vaultTemplate.opsForVersionedKeyValue(kvMount).delete(path);
            log.info("Vault KV metadata deleted path={}", path);
        } catch (Exception e) {
            log.warn("Vault KV delete path={}: {}", path, e.getMessage());
        }
    }

    /**
     * Checks connectivity (sys health or version).
     */
    public boolean probe() {
        try {
            vaultTemplate.opsForSys().health();
            return true;
        } catch (Exception e) {
            log.warn("Vault health probe failed: {}", e.getMessage());
            return false;
        }
    }

    private static String normalizePath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
