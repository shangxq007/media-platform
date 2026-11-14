package com.example.platform.storage.api;

import java.io.InputStream;

/** Storage-owned admission of a bounded source stream into a durable scoped placement. */
public interface StorageSourceUploadPort {
    StorageOutputPort.WrittenOutput writeSource(StorageOwnershipScope owner,
            IssuanceIdempotencyKey key, InputStream content, long declaredLength, String contentType);
}
