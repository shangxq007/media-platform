package com.example.platform.storage.api;

/** Storage-owned admission of bounded source bytes into a durable scoped placement. */
public interface StorageSourceUploadPort {
    StorageOutputPort.WrittenOutput writeSource(StorageOwnershipScope owner,
            IssuanceIdempotencyKey key, byte[] bytes, String contentType);
}
