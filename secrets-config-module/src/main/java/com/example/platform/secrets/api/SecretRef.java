package com.example.platform.secrets.api;

import java.util.Objects;

/**
 * Reference to a secret value.
 *
 * <p>Contains only reference metadata (backend/path/field), never the secret value itself.</p>
 *
 * @param backend the backend type (e.g., "vault", "env")
 * @param path    the secret path within the backend
 * @param field   optional field name for map-type secrets
 */
public record SecretRef(String backend, String path, String field) {

    /**
     * Backend constant for Vault KV2 secrets.
     */
    public static final String BACKEND_VAULT = "vault";

    /**
     * Backend constant for environment variable secrets.
     */
    public static final String BACKEND_ENV = "env";

    /**
     * Parses a secret reference string.
     *
     * <p>Supported formats:</p>
     * <ul>
     *   <li>{@code vault:path#field} - Vault KV2 secret with optional field</li>
     *   <li>{@code ${env:VAR:default}} - Environment variable with optional default</li>
     *   <li>{@code name:key} - Simple name:key format (defaults to "vault" backend)</li>
     * </ul>
     *
     * @param ref the reference string
     * @return the parsed SecretRef
     * @throws IllegalArgumentException if the format is invalid
     */
    public static SecretRef parse(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Secret reference cannot be null or blank");
        }

        // Format: vault:path#field
        if (ref.startsWith("vault:")) {
            String rest = ref.substring(6);
            int hashIndex = rest.indexOf('#');
            if (hashIndex >= 0) {
                return new SecretRef(BACKEND_VAULT, rest.substring(0, hashIndex), rest.substring(hashIndex + 1));
            }
            return new SecretRef(BACKEND_VAULT, rest, null);
        }

        // Format: ${env:VAR:default}
        if (ref.startsWith("${env:") && ref.endsWith("}")) {
            String content = ref.substring(6, ref.length() - 1);
            return new SecretRef(BACKEND_ENV, content, null);
        }

        // Format: name:key (simple)
        int colonIndex = ref.indexOf(':');
        if (colonIndex >= 0) {
            return new SecretRef(BACKEND_VAULT, ref.substring(0, colonIndex), ref.substring(colonIndex + 1));
        }

        throw new IllegalArgumentException("Invalid secret reference format: " + ref);
    }

    /**
     * Encodes this reference back to a string.
     *
     * @return the encoded reference string
     */
    public String encode() {
        if (BACKEND_ENV.equals(backend)) {
            return "${env:" + path + "}";
        }
        if (field != null && !field.isBlank()) {
            return backend + ":" + path + "#" + field;
        }
        return backend + ":" + path;
    }

    @Override
    public String toString() {
        return encode();
    }
}
