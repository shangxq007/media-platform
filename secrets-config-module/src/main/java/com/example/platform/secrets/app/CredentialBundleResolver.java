package com.example.platform.secrets.app;

import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.CredentialBundlePort;
import com.example.platform.secrets.api.port.SecretResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves delivery (and other) credential maps from {@code credential_ref} or legacy inline JSON.
 */
@Service
public class CredentialBundleResolver implements CredentialBundlePort {

    private static final Logger log = LoggerFactory.getLogger(CredentialBundleResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SecretResolver secretResolver;

    public CredentialBundleResolver(SecretResolver secretResolver) {
        this.secretResolver = secretResolver;
    }

    public Map<String, String> resolve(String credentialRef, String legacyCredentialJson) {
        if (credentialRef != null && !credentialRef.isBlank()) {
            try {
                SecretRef ref = SecretRef.parse(credentialRef);
                Map<String, String> map = secretResolver.resolveMap(ref);
                if (!map.isEmpty()) {
                    return map;
                }
                log.warn("credential_ref {} resolved to empty map, trying legacy JSON if present", credentialRef);
            } catch (Exception e) {
                log.warn("Failed to resolve credential_ref {}: {}", credentialRef, e.getMessage());
                if (legacyCredentialJson == null || legacyCredentialJson.isBlank()) {
                    throw new IllegalStateException("Cannot resolve credentials from ref: " + credentialRef, e);
                }
            }
        }
        return parseLegacyJson(legacyCredentialJson);
    }

    public boolean hasCredentials(String credentialRef, String legacyCredentialJson) {
        if (credentialRef != null && !credentialRef.isBlank()) {
            return true;
        }
        return legacyCredentialJson != null && !legacyCredentialJson.isBlank();
    }

    private static Map<String, String> parseLegacyJson(String legacyCredentialJson) {
        if (legacyCredentialJson == null || legacyCredentialJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = MAPPER.readValue(legacyCredentialJson, new TypeReference<>() {});
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid legacy credential_json", e);
        }
    }
}
