package com.example.platform.storage.domain;

/**
 * Object metadata returned from {@link BlobStorage#listObjects(String, String, int)}.
 */
public record StoredObject(String bucket, String objectKey, long sizeBytes) {

    public String toStorageUri(String providerCode) {
        return providerCode + "://" + bucket + "/" + objectKey;
    }
}
