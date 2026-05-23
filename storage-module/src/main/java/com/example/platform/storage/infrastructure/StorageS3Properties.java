package com.example.platform.storage.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible blob storage (AWS S3, MinIO, RustFS, Cloudflare R2, etc.).
 */
@ConfigurationProperties(prefix = "storage.s3")
public class StorageS3Properties {

    private boolean enabled = false;
    /** {@link S3CompatibilityMode#R2} applies region/path-style/chunked-encoding presets for Cloudflare R2. */
    private S3CompatibilityMode compatibility = S3CompatibilityMode.GENERIC;
    /** Cloudflare account ID; used to build {@code https://{accountId}.r2.cloudflarestorage.com} when endpoint is unset. */
    private String accountId;
    private String region = "us-east-1";
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccess = true;
    /** Must be {@code false} for R2 ({@link S3CompatibilityMode#R2} forces this). */
    private boolean chunkedEncodingEnabled = true;
    private String defaultBucket = "render-cache";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public S3CompatibilityMode getCompatibility() {
        return compatibility;
    }

    public void setCompatibility(S3CompatibilityMode compatibility) {
        this.compatibility = compatibility;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public boolean isChunkedEncodingEnabled() {
        return chunkedEncodingEnabled;
    }

    public void setChunkedEncodingEnabled(boolean chunkedEncodingEnabled) {
        this.chunkedEncodingEnabled = chunkedEncodingEnabled;
    }

    public String getDefaultBucket() {
        return defaultBucket;
    }

    public void setDefaultBucket(String defaultBucket) {
        this.defaultBucket = defaultBucket;
    }
}
