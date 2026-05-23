package com.example.platform.render.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Remote render artifact cache (S3 / tenant-scoped prefix).
 */
@ConfigurationProperties(prefix = "render.cache")
public class RenderCacheProperties {

    /** When true, resolve blank/reuse URIs via {@link #remoteUriPrefix} + cacheKey. */
    private boolean remoteEnabled = false;

    /** e.g. {@code s3://tenant-demo/cache} or {@code tenant://cache} */
    private String remoteUriPrefix = "s3://tenant-cache/render";

    /** When true, segment artifacts are uploaded to {@link com.example.platform.storage.domain.BlobStorage} after render. */
    private boolean uploadEnabled = false;

    /** When true, compute SHA-256 for cache entries and validate on incremental reuse. */
    private boolean contentHashEnabled = false;

    /**
     * When true, hash mismatch forces affected tasks to re-execute instead of silent reuse drop only.
     */
    private boolean invalidateOnHashMismatch = true;

    /** When true, {@link com.example.platform.render.app.cache.RenderCacheCleanupScheduler} removes stale remote cache objects. */
    private boolean cleanupEnabled = false;

    /** Delete remote cache objects for jobs completed longer than this many days ago. */
    private int retentionDays = 30;

    /** Fixed delay between cleanup runs (ISO-8601 duration, e.g. PT24H). */
    private String cleanupInterval = "PT24H";

    /**
     * Optional outbound webhook URL for {@code render.cache.hash_invalidated} (in addition to notification-module).
     */
    private boolean webhookEnabled = false;

    private String webhookUrl = "";

    private String webhookSecret = "";

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(String cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public void setWebhookEnabled(boolean webhookEnabled) {
        this.webhookEnabled = webhookEnabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isContentHashEnabled() {
        return contentHashEnabled;
    }

    public void setContentHashEnabled(boolean contentHashEnabled) {
        this.contentHashEnabled = contentHashEnabled;
    }

    public boolean isInvalidateOnHashMismatch() {
        return invalidateOnHashMismatch;
    }

    public void setInvalidateOnHashMismatch(boolean invalidateOnHashMismatch) {
        this.invalidateOnHashMismatch = invalidateOnHashMismatch;
    }

    public boolean isUploadEnabled() {
        return uploadEnabled;
    }

    public void setUploadEnabled(boolean uploadEnabled) {
        this.uploadEnabled = uploadEnabled;
    }

    public boolean isRemoteEnabled() {
        return remoteEnabled;
    }

    public void setRemoteEnabled(boolean remoteEnabled) {
        this.remoteEnabled = remoteEnabled;
    }

    public String getRemoteUriPrefix() {
        return remoteUriPrefix;
    }

    public void setRemoteUriPrefix(String remoteUriPrefix) {
        this.remoteUriPrefix = remoteUriPrefix;
    }
}
