package com.example.platform.storage.infrastructure;

import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import com.example.platform.storage.domain.StoredObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3-compatible {@link BlobStorage} for render cache and artifact persistence.
 *
 * <p>S3Client and S3Presigner are initialized lazily on first use to avoid
 * network calls (DNS resolution, HTTP client init) during Spring startup.
 * This prevents startup hangs when the S3/R2 endpoint is slow or unreachable
 * at boot time.</p>
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
public class S3BlobStorageProvider implements BlobStorage {

    private static final Logger log = LoggerFactory.getLogger(S3BlobStorageProvider.class);

    private final StorageS3Properties properties;
    private volatile S3Client s3Client;
    private volatile S3Presigner presigner;

    public S3BlobStorageProvider(StorageS3Properties properties) {
        this.properties = properties;
        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(properties);
        log.info("S3BlobStorageProvider enabled compatibility={} region={} endpoint={} (client will be initialized on first use)",
                resolved.compatibilityMode(), resolved.region(), resolved.endpoint());
    }

    private S3Client getClient() {
        S3Client result = s3Client;
        if (result == null) {
            synchronized (this) {
                result = s3Client;
                if (result == null) {
                    log.info("Lazily initializing S3Client...");
                    s3Client = result = buildClient(properties);
                }
            }
        }
        return result;
    }

    private S3Presigner getPresigner() {
        S3Presigner result = presigner;
        if (result == null) {
            synchronized (this) {
                result = presigner;
                if (result == null) {
                    log.info("Lazily initializing S3Presigner...");
                    presigner = result = buildPresigner(properties);
                }
            }
        }
        return result;
    }

    @Override
    public String code() {
        return "s3StorageProvider";
    }

    @Override
    public StorageObjectRef put(PutObjectCommand command) {
        String bucket = command.bucket() != null ? command.bucket() : properties.getDefaultBucket();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(command.objectKey())
                .contentType(command.contentType())
                .build();
        RequestBody body;
        if (command.isFileBased()) {
            body = RequestBody.fromFile(command.contentPath().toFile());
        } else {
            body = RequestBody.fromBytes(command.content());
        }
        getClient().putObject(request, body);
        return new StorageObjectRef(code(), bucket, command.objectKey());
    }

    @Override
    public boolean delete(String bucket, String objectKey) {
        String resolvedBucket = bucket != null && !bucket.isBlank() ? bucket : properties.getDefaultBucket();
        getClient().deleteObject(DeleteObjectRequest.builder()
                .bucket(resolvedBucket)
                .key(objectKey)
                .build());
        return true;
    }

    @Override
    public List<StoredObject> listObjects(String bucket, String prefix, int maxKeys) {
        String resolvedBucket = bucket != null && !bucket.isBlank() ? bucket : properties.getDefaultBucket();
        int limit = Math.max(1, maxKeys);
        List<StoredObject> objects = new ArrayList<>();
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                    .bucket(resolvedBucket)
                    .maxKeys(Math.min(1000, limit - objects.size()));
            if (prefix != null && !prefix.isBlank()) {
                builder.prefix(prefix);
            }
            if (continuationToken != null) {
                builder.continuationToken(continuationToken);
            }
            ListObjectsV2Response response = getClient().listObjectsV2(builder.build());
            for (S3Object obj : response.contents()) {
                if (obj.key() == null || obj.key().endsWith("/")) {
                    continue;
                }
                objects.add(new StoredObject(resolvedBucket, obj.key(), obj.size()));
                if (objects.size() >= limit) {
                    return objects;
                }
            }
            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (continuationToken != null && objects.size() < limit);
        return objects;
    }

    @Override
    public Optional<byte[]> get(String bucket, String objectKey) {
        String resolvedBucket = bucket != null && !bucket.isBlank() ? bucket : properties.getDefaultBucket();
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(resolvedBucket)
                .key(objectKey)
                .build();
        try {
            return Optional.of(getClient().getObjectAsBytes(request).asByteArray());
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (isObjectNotFound(e)) {
                log.debug("S3 object not found: bucket={} key={}", resolvedBucket, objectKey);
                return Optional.empty();
            }
            throw new IllegalStateException(
                    "S3 get failed: bucket=" + resolvedBucket + " key=" + objectKey
                            + " status=" + e.statusCode() + " error=" + e.awsErrorDetails().errorCode(), e);
        }
    }

    /**
     * Checks if the S3Exception indicates that the object (or bucket) does not exist.
     * Returns true for 404 NoSuchKey/NotFound, false for other errors (403, 500, etc.).
     */
    private static boolean isObjectNotFound(software.amazon.awssdk.services.s3.model.S3Exception e) {
        if (e.statusCode() != 404) {
            return false;
        }
        String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;
        return "NoSuchKey".equals(errorCode) || "NotFound".equals(errorCode);
    }

    @Override
    public String presign(String objectKey) {
        return presign(properties.getDefaultBucket(), objectKey);
    }

    @Override
    public String presign(String bucket, String objectKey) {
        String resolvedBucket = bucket != null && !bucket.isBlank() ? bucket : properties.getDefaultBucket();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(java.time.Duration.ofHours(1))
                .getObjectRequest(b -> b.bucket(resolvedBucket).key(objectKey))
                .build();
        return getPresigner().presignGetObject(presignRequest).url().toString();
    }

    private static S3Client buildClient(StorageS3Properties properties) {
        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(properties);
        var builder = S3Client.builder()
                .region(Region.of(resolved.region()))
                .serviceConfiguration(buildServiceConfiguration(resolved));
        if (resolved.endpointUri() != null) {
            builder.endpointOverride(resolved.endpointUri());
        }
        if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank() && properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
        }
        return builder.build();
    }

    private static S3Presigner buildPresigner(StorageS3Properties properties) {
        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(properties);
        var builder = S3Presigner.builder()
                .region(Region.of(resolved.region()))
                .serviceConfiguration(buildServiceConfiguration(resolved));
        if (resolved.endpointUri() != null) {
            builder.endpointOverride(resolved.endpointUri());
        }
        if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank() && properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
        }
        return builder.build();
    }

    private static S3Configuration buildServiceConfiguration(S3ClientSettingsResolver.Resolved resolved) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(resolved.pathStyleAccess())
                .chunkedEncodingEnabled(resolved.chunkedEncodingEnabled())
                .build();
    }
}
