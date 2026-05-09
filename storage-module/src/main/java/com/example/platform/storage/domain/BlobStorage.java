package com.example.platform.storage.domain;

public interface BlobStorage {
    String code();
    StorageObjectRef put(PutObjectCommand command);
    String presign(String objectKey);
}
