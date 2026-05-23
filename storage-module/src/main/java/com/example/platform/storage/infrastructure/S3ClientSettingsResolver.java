package com.example.platform.storage.infrastructure;

import java.net.URI;

/**
 * Resolves effective S3 client settings, including {@link S3CompatibilityMode#R2} presets.
 */
public final class S3ClientSettingsResolver {

    private S3ClientSettingsResolver() {}

    public static Resolved resolve(StorageS3Properties properties) {
        S3CompatibilityMode mode = properties.getCompatibility() != null
                ? properties.getCompatibility()
                : S3CompatibilityMode.GENERIC;

        String endpoint = resolveEndpoint(properties, mode);
        String region = properties.getRegion() != null ? properties.getRegion() : "us-east-1";
        boolean pathStyle = properties.isPathStyleAccess();
        boolean chunkedEncoding = properties.isChunkedEncodingEnabled();

        if (mode == S3CompatibilityMode.R2) {
            region = resolveR2Region(region);
            pathStyle = true;
            chunkedEncoding = false;
        }

        return new Resolved(endpoint, region, pathStyle, chunkedEncoding, mode);
    }

    static String resolveEndpoint(StorageS3Properties properties, S3CompatibilityMode mode) {
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            return properties.getEndpoint().trim();
        }
        if (mode == S3CompatibilityMode.R2
                && properties.getAccountId() != null
                && !properties.getAccountId().isBlank()) {
            return "https://" + properties.getAccountId().trim() + ".r2.cloudflarestorage.com";
        }
        return null;
    }

    private static String resolveR2Region(String configured) {
        if (configured == null || configured.isBlank() || "us-east-1".equals(configured)) {
            return "auto";
        }
        return configured;
    }

    public record Resolved(
            String endpoint,
            String region,
            boolean pathStyleAccess,
            boolean chunkedEncodingEnabled,
            S3CompatibilityMode compatibilityMode) {

        public URI endpointUri() {
            return endpoint != null && !endpoint.isBlank() ? URI.create(endpoint) : null;
        }
    }
}
