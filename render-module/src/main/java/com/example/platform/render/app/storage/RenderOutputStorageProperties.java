package com.example.platform.render.app.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for render output storage provider selection.
 *
 * <p>Controls whether render outputs are stored locally or in
 * S3-compatible internal object storage.</p>
 *
 * <p>Configuration example:
 * <pre>
 * storage:
 *   output:
 *     provider: local | s3-compatible
 *     s3-bucket: media-platform-render-output
 *     s3-key-prefix: projects
 * </pre>
 *
 * <p>Default provider is {@code local} for backward compatibility.
 * When set to {@code s3-compatible}, render outputs are uploaded to the
 * configured S3-compatible internal bucket.</p>
 */
@ConfigurationProperties(prefix = "storage.output")
public class RenderOutputStorageProperties {

    /**
     * Output storage provider type.
     * <ul>
     *   <li>{@code local} — store output files on local filesystem (default)</li>
     *   <li>{@code s3-compatible} — upload output files to S3-compatible internal storage</li>
     * </ul>
     */
    private String provider = "local";

    /**
     * S3 bucket for render output storage.
     * Only used when provider is {@code s3-compatible}.
     * Falls back to storage.s3.default-bucket if not set.
     */
    private String s3Bucket;

    /**
     * Key prefix for render output objects.
     * The full key is: {s3KeyPrefix}/{projectId}/render-jobs/{renderJobId}/outputs/{filename}
     */
    private String s3KeyPrefix = "projects";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    public String getS3KeyPrefix() {
        return s3KeyPrefix;
    }

    public void setS3KeyPrefix(String s3KeyPrefix) {
        this.s3KeyPrefix = s3KeyPrefix;
    }

    /**
     * Check if S3-compatible output storage is configured.
     */
    public boolean isS3Compatible() {
        return "s3-compatible".equalsIgnoreCase(provider);
    }
}
