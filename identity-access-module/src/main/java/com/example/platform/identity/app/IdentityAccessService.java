package com.example.platform.identity.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IdentityAccessService {

    private final IdentityProperties identityProperties;
    private final ApiKeyRepository apiKeyRepository;

    public IdentityAccessService(IdentityProperties identityProperties, ApiKeyRepository apiKeyRepository) {
        this.identityProperties = identityProperties;
        this.apiKeyRepository = apiKeyRepository;
        bootstrapFromProperties();
    }

    private void bootstrapFromProperties() {
        identityProperties.getApiKeys().forEach((plainKey, principal) -> {
            String hashedKey = hashApiKey(plainKey);
            String fingerprint = fingerprint(plainKey);
            String id = "ak_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            ApiKeyRecord record = new ApiKeyRecord(id, "default-tenant", fingerprint, hashedKey, principal, Instant.now(), null, null);
            apiKeyRepository.save(record);
        });
    }

    public String hashApiKey(String plainKey) {
        if (plainKey == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withLowerCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public String fingerprint(String plainKey) {
        String hash = hashApiKey(plainKey);
        if (hash == null) {
            return null;
        }
        return hash.substring(0, 8);
    }

    public Map<String, Object> overview() {
        List<ApiKeyRecord> allRecords = apiKeyRepository.findAll();
        long activeCount = allRecords.stream().filter(r -> !r.isRevoked()).count();
        long revokedCount = allRecords.stream().filter(ApiKeyRecord::isRevoked).count();
        return Map.of(
                "module", "identity-access-module",
                "status", "active",
                "description", "身份与访问控制模块，负责 API Key 与服务账号鉴权骨架。",
                "apiKeyAuthEnabled", identityProperties.isApiKeyAuthEnabled(),
                "apiKeyCount", allRecords.size(),
                "activeKeyCount", activeCount,
                "revokedKeyCount", revokedCount
        );
    }

    public boolean validateApiKey(String apiKey) {
        if (apiKey == null) {
            return false;
        }
        String hashedKey = hashApiKey(apiKey);
        return apiKeyRepository.findByHashedKey(hashedKey)
                .map(record -> !record.isRevoked())
                .orElse(false);
    }

    public String principalOf(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        String hashedKey = hashApiKey(apiKey);
        return apiKeyRepository.findByHashedKey(hashedKey)
                .filter(record -> !record.isRevoked())
                .map(ApiKeyRecord::principal)
                .orElse(null);
    }

    /**
     * Resolve the tenantId bound to the given API key.
     *
     * @return the tenantId, or {@code null} if the key is invalid or revoked
     */
    public String tenantIdOf(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        String hashedKey = hashApiKey(apiKey);
        return apiKeyRepository.findByHashedKey(hashedKey)
                .filter(record -> !record.isRevoked())
                .map(ApiKeyRecord::tenantId)
                .orElse(null);
    }

    public List<Map<String, String>> serviceAccounts() {
        return apiKeyRepository.findAll().stream()
                .filter(r -> !r.isRevoked())
                .map(r -> Map.of("principal", r.principal()))
                .distinct()
                .toList();
    }

    public void recordUsage(String apiKey) {
        if (apiKey == null) {
            return;
        }
        String hashedKey = hashApiKey(apiKey);
        apiKeyRepository.updateLastUsedAt(hashedKey, OffsetDateTime.now());
    }

    public boolean revoke(String apiKey) {
        if (apiKey == null) {
            return false;
        }
        String hashedKey = hashApiKey(apiKey);
        return apiKeyRepository.findByHashedKey(hashedKey)
                .filter(record -> !record.isRevoked())
                .map(record -> {
                    apiKeyRepository.updateRevokedAt(hashedKey, OffsetDateTime.now());
                    return true;
                })
                .orElse(false);
    }

    public ApiKeyRecord findRecordByFingerprint(String fingerprint) {
        if (fingerprint == null) {
            return null;
        }
        return apiKeyRepository.findByFingerprint(fingerprint).orElse(null);
    }

    public List<ApiKeyRecord> listRecords() {
        return apiKeyRepository.findAll();
    }

    public void storeRecord(ApiKeyRecord record) {
        apiKeyRepository.save(record);
    }
}
