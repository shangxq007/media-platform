package com.example.platform.storage.domain;

public record StorageObjectRef(String provider, String bucket, String objectKey) {
    public String toStorageUri() {
        return provider + "://" + bucket + "/" + objectKey;
    }
}
