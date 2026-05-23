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
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
public class S3BlobStorageProvider implements BlobStorage {

    private static final Logger log = LoggerFactory.getLogger(S3BlobStorageProvider.class);

    private final StorageS3Properties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3BlobStorageProvider(StorageS3Properties properties) {
        this.properties = properties;
        this.s3Client = buildClient(properties);
        this.presigner = buildPresigner(properties);
        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(properties);
        log.info("S3BlobStorageProvider enabled compatibility={} region={} endpoint={}",
                resolved.compatibilityMode(), resolved.region(), resolved.endpoint());
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
        s3Client.putObject(request, RequestBody.fromBytes(command.content()));
        return new StorageObjectRef(code(), bucket, command.objectKey());
    }

    @Override
    public boolean delete(String bucket, String objectKey) {
        String resolvedBucket = bucket != null && !bucket.isBlank() ? bucket : properties.getDefaultBucket();
        s3Client.deleteObject(DeleteObjectRequest.builder()
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
            ListObjectsV2Response response = s3Client.listObjectsV2(builder.build());
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
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        return Optional.of(s3Client.getObjectAsBytes(request).asByteArray());
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
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    private static S3Client buildClient(StorageS3Properties properties) {
        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(properties);
        var builder = S3Client.builder()
                .region(Region.of(resolved.region()))
                .serviceConfiguration(buildServiceConfiguration(resolved));
        if (resolved.endpointUri() != null) {
            builder.endpointOverride(resolved.endpointUri());
        }
        if (properties.getAccessKey() != null && properties.getSecretKey() != null) {
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
        if (properties.getAccessKey() != null && properties.getSecretKey() != null) {
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
