package com.example.platform.storage.domain;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface BlobStorage {
    String code();

    StorageObjectRef put(PutObjectCommand command);

    String presign(String objectKey);

    /**
     * Presigned GET URL for a bucket + object key. Default delegates to {@link #presign(String)}.
     */
    default String presign(String bucket, String objectKey) {
        return presign(objectKey);
    }

    /**
     * Presigns a {@code provider://bucket/objectKey} URI when parseable.
     */
    default Optional<String> presignStorageUri(String storageUri) {
        return parseUri(storageUri).map(ref -> presign(ref.bucket(), ref.objectKey()));
    }

    /**
     * Reads object bytes when supported (e.g. S3 / local FS). Default: not available.
     */
    default Optional<byte[]> get(String bucket, String objectKey) {
        return Optional.empty();
    }

    /**
     * Deletes an object when supported. Returns {@code true} if deleted or already absent.
     */
    default boolean delete(String bucket, String objectKey) {
        return false;
    }

    default boolean deleteStorageUri(String storageUri) {
        return parseUri(storageUri).map(ref -> delete(ref.bucket(), ref.objectKey())).orElse(false);
    }

    /**
     * Lists objects under a bucket/prefix when supported by the provider implementation.
     */
    default List<StoredObject> listObjects(String bucket, String prefix, int maxKeys) {
        return Collections.emptyList();
    }

    static Optional<StorageObjectRef> parseUri(String storageUri) {
        if (storageUri == null || storageUri.isBlank()) {
            return Optional.empty();
        }
        int schemeEnd = storageUri.indexOf("://");
        if (schemeEnd <= 0) {
            return Optional.empty();
        }
        String provider = storageUri.substring(0, schemeEnd);
        String remainder = storageUri.substring(schemeEnd + 3);
        int slash = remainder.indexOf('/');
        if (slash <= 0) {
            return Optional.empty();
        }
        String bucket = remainder.substring(0, slash);
        String objectKey = remainder.substring(slash + 1);
        return Optional.of(new StorageObjectRef(provider, bucket, objectKey));
    }
}
