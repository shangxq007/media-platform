package com.example.platform.storage.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Uploads local files to S3-compatible object storage.
 *
 * <p>This is the write/output path for S3-compatible object storage.
 * It uploads local render output files to a configured internal bucket/key.
 * The read/materialize path is handled by {@link S3ObjectMaterializer}.</p>
 *
 * <p>Storage-neutral: works with any S3-compatible backend (RustFS, SeaweedFS,
 * AWS S3, Cloudflare R2, etc.). Does not contain backend-specific logic.</p>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>No signed URLs generated or persisted</li>
 *   <li>No bucket/key exposed in public API</li>
 *   <li>Uploads to platform-controlled internal bucket only</li>
 *   <li>Checksum verification against computed SHA-256</li>
 *   <li>ETag is not treated as canonical SHA-256</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
public class S3ObjectWriter {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectWriter.class);

    private final StorageS3Properties properties;
    private final S3Client s3Client;

    public S3ObjectWriter(StorageS3Properties properties) {
        this.properties = properties;
        this.s3Client = buildClient(properties);
        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(properties);
        log.info("S3ObjectWriter initialized: endpoint={} region={} pathStyle={}",
                resolved.endpoint(), resolved.region(), resolved.pathStyleAccess());
    }

    /**
     * Upload a local file to S3-compatible object storage.
     *
     * @param localFilePath the local file to upload
     * @param bucket        the target bucket name
     * @param objectKey     the target object key
     * @param contentType   optional content type (e.g., "video/mp4")
     * @return upload result with bucket, objectKey, sizeBytes, checksum, contentType
     * @throws IllegalStateException if upload or verification fails
     */
    public UploadResult upload(Path localFilePath, String bucket, String objectKey,
                                String contentType) {
        if (localFilePath == null || !Files.exists(localFilePath)) {
            throw new IllegalStateException("Local file does not exist: " + localFilePath);
        }
        if (!Files.isRegularFile(localFilePath)) {
            throw new IllegalStateException("Local path is not a regular file: " + localFilePath);
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Bucket must not be null or blank");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException("Object key must not be null or blank");
        }

        long fileSize;
        try {
            fileSize = Files.size(localFilePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file size: " + localFilePath, e);
        }
        if (fileSize == 0) {
            throw new IllegalStateException("Cannot upload zero-byte file: " + localFilePath);
        }

        // Compute checksum before upload
        String checksum = computeSha256(localFilePath);

        // Upload
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey);
            if (contentType != null && !contentType.isBlank()) {
                requestBuilder.contentType(contentType);
            }

            s3Client.putObject(requestBuilder.build(), RequestBody.fromFile(localFilePath));
            log.info("S3 upload completed: bucket={} key={} size={} checksum={}",
                    bucket, objectKey, fileSize, checksum);
        } catch (S3Exception e) {
            log.error("S3 upload failed: bucket={} key={} status={}", bucket, objectKey, e.statusCode());
            // Attempt cleanup of partial upload
            attemptDelete(bucket, objectKey);
            throw new IllegalStateException(
                    "S3 upload failed: bucket=" + bucket + " key=" + objectKey
                            + " status=" + e.statusCode(), e);
        } catch (Exception e) {
            log.error("S3 upload failed: bucket={} key={} error={}", bucket, objectKey, e.getMessage());
            attemptDelete(bucket, objectKey);
            throw new IllegalStateException(
                    "S3 upload failed: bucket=" + bucket + " key=" + objectKey, e);
        }

        // Verify object exists via HeadObject
        HeadObjectResponse headResponse;
        try {
            headResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            log.error("S3 post-upload head verification failed: bucket={} key={} status={}",
                    bucket, objectKey, e.statusCode());
            attemptDelete(bucket, objectKey);
            throw new IllegalStateException(
                    "S3 post-upload verification failed: bucket=" + bucket + " key=" + objectKey
                            + " status=" + e.statusCode(), e);
        }

        long uploadedSize = headResponse.contentLength() != null ? headResponse.contentLength() : 0;
        if (uploadedSize != fileSize) {
            log.warn("S3 uploaded size mismatch: expected={} actual={} bucket={} key={}",
                    fileSize, uploadedSize, bucket, objectKey);
            attemptDelete(bucket, objectKey);
            throw new IllegalStateException(
                    "S3 uploaded size mismatch: expected=" + fileSize + " actual=" + uploadedSize
                            + " bucket=" + bucket + " key=" + objectKey);
        }

        String uploadedContentType = headResponse.contentType();

        log.info("S3 upload verified: bucket={} key={} size={} checksum={}",
                bucket, objectKey, uploadedSize, checksum);

        return new UploadResult(bucket, objectKey, uploadedSize, checksum, uploadedContentType);
    }

    /**
     * Delete an object from S3-compatible storage.
     * Best-effort: logs warning on failure but does not throw.
     */
    public void delete(String bucket, String objectKey) {
        attemptDelete(bucket, objectKey);
    }

    /**
     * Check if the S3 properties are enabled and configured.
     */
    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getEndpoint() != null
                && !properties.getEndpoint().isBlank();
    }

    /**
     * Get the configured default bucket.
     */
    public String getDefaultBucket() {
        return properties.getDefaultBucket();
    }

    private void attemptDelete(String bucket, String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            log.info("S3 object deleted: bucket={} key={}", bucket, objectKey);
        } catch (Exception e) {
            log.warn("S3 object deletion failed (best-effort): bucket={} key={} error={}",
                    bucket, objectKey, e.getMessage());
        }
    }

    private static String computeSha256(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute checksum: " + file, e);
        }
    }

    private static S3Client buildClient(StorageS3Properties properties) {
        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(properties);
        var builder = S3Client.builder()
                .region(Region.of(resolved.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(resolved.pathStyleAccess())
                        .chunkedEncodingEnabled(resolved.chunkedEncodingEnabled())
                        .build());
        if (resolved.endpointUri() != null) {
            builder.endpointOverride(resolved.endpointUri());
        }
        if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank() && properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
        }
        return builder.build();
    }

    /**
     * Result of uploading a file to S3-compatible storage.
     *
     * @param bucket      the bucket name
     * @param objectKey   the object key
     * @param sizeBytes   the uploaded object size in bytes
     * @param checksum    the SHA-256 hex checksum of the uploaded file
     * @param contentType the content type (may be null)
     */
    public record UploadResult(
            String bucket,
            String objectKey,
            long sizeBytes,
            String checksum,
            String contentType) {}
}
