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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Materializes objects from S3-compatible storage to local temporary files.
 *
 * <p>This is the read/materialize path for S3-compatible object storage.
 * It downloads objects to local temp files for render input materialization.
 * The write/output path is out of scope for R10A (deferred to R10B).</p>
 *
 * <p>Storage-neutral: works with any S3-compatible backend (RustFS, SeaweedFS,
 * AWS S3, Cloudflare R2, etc.). Does not contain backend-specific logic.</p>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>No signed URLs generated or persisted</li>
 *   <li>No bucket/key exposed in public API</li>
 *   <li>Local temp files only returned to internal runtime code</li>
 *   <li>Checksum verification against stored SHA-256</li>
 * </ul>
 *
 * <p>S3Client is initialized lazily on first use to avoid network calls
 * (DNS resolution, HTTP client init) during Spring startup.</p>
 */
@Component
@ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
public class S3ObjectMaterializer {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectMaterializer.class);

    private final StorageS3Properties properties;
    private volatile S3Client s3Client;

    public S3ObjectMaterializer(StorageS3Properties properties) {
        this.properties = properties;
        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(properties);
        log.info("S3ObjectMaterializer initialized: endpoint={} region={} pathStyle={} (client will be initialized on first use)",
                resolved.endpoint(), resolved.region(), resolved.pathStyleAccess());
    }

    private S3Client getClient() {
        S3Client result = s3Client;
        if (result == null) {
            synchronized (this) {
                result = s3Client;
                if (result == null) {
                    log.info("Lazily initializing S3ObjectMaterializer S3Client...");
                    s3Client = result = buildClient(properties);
                }
            }
        }
        return result;
    }

    /**
     * Materialize an S3 object to a local temporary file.
     *
     * @param bucket          the S3 bucket name (StorageReference.rootPath)
     * @param objectKey       the S3 object key (StorageReference.relativePath)
     * @param expectedChecksum optional expected SHA-256 checksum for verification
     * @return materialization result, or empty if object not found
     */
    public Optional<MaterializedObject> materialize(String bucket, String objectKey,
                                                     String expectedChecksum) {
        if (bucket == null || bucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            log.warn("S3 materialize: invalid bucket or key: bucket={} key={}", bucket, objectKey);
            return Optional.empty();
        }

        // Head object to verify existence and get metadata
        HeadObjectResponse headResponse;
        try {
            headResponse = getClient().headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                log.debug("S3 object not found: bucket={} key={}", bucket, objectKey);
                return Optional.empty();
            }
            throw new IllegalStateException(
                    "S3 head failed: bucket=" + bucket + " key=" + objectKey
                            + " status=" + e.statusCode(), e);
        }

        long contentLength = headResponse.contentLength() != null ? headResponse.contentLength() : 0;
        String contentType = headResponse.contentType();

        // Download to temp file
        Path tempFile;
        try {
            tempFile = Files.createTempFile("s3-materialize-", ".tmp");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp file for S3 materialization", e);
        }

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();

            // Use getObjectAsBytes for broad S3-compatible backend support
            byte[] objectBytes = getClient().getObjectAsBytes(getRequest).asByteArray();

            if (objectBytes.length == 0) {
                log.warn("S3 downloaded zero-byte object: bucket={} key={}", bucket, objectKey);
                Files.deleteIfExists(tempFile);
                return Optional.empty();
            }

            // Write to temp file
            Files.write(tempFile, objectBytes);

            // Compute checksum and verify if expected
            String checksum = computeSha256Bytes(objectBytes);
            if (expectedChecksum != null && !expectedChecksum.isBlank()) {
                if (!checksum.equalsIgnoreCase(expectedChecksum)) {
                    log.warn("S3 checksum mismatch: bucket={} key={} expected={} actual={}",
                            bucket, objectKey, expectedChecksum, checksum);
                    Files.deleteIfExists(tempFile);
                    return Optional.empty();
                }
            }

            log.info("S3 object materialized: bucket={} key={} size={} checksum={}",
                    bucket, objectKey, objectBytes.length, checksum);

            return Optional.of(new MaterializedObject(tempFile, objectBytes.length, checksum, contentType));

        } catch (Exception e) {
            // Clean up temp file on failure
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException(
                    "S3 download failed: bucket=" + bucket + " key=" + objectKey, e);
        }
    }

    /**
     * Check if the S3 properties are enabled and configured.
     */
    /**
     * Create presigned GET URL for S3/R2 object access.
     * Access-layer only, never persisted, never exposes bucket/key separately.
     */
    public String createPresignedGetUrl(String bucket, String objectKey, java.time.Duration ttl) {
        S3Client client = getClient();
        S3Presigner presigner = S3Presigner.builder()
                .credentialsProvider(client.serviceClientConfiguration().credentialsProvider())
                .region(client.serviceClientConfiguration().region())
                .endpointOverride(client.serviceClientConfiguration().endpointOverride().orElse(null))
                .build();

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getEndpoint() != null
                && !properties.getEndpoint().isBlank();
    }

    private static String computeSha256Bytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
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
        if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank()
                && properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
        }
        return builder.build();
    }

    /**
     * Result of materializing an S3 object to a local file.
     *
     * @param localPath   the local temporary file path
     * @param sizeBytes   the object size in bytes
     * @param checksum    the SHA-256 hex checksum
     * @param contentType the S3 content type (may be null)
     */
    public record MaterializedObject(Path localPath, long sizeBytes, String checksum, String contentType) {}
}
