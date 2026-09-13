package com.example.platform.storage.api;
import com.example.platform.storage.contract.StorageReference;
/** Owner boundary for existing preview upload and uploaded-media reference registration. */
public interface StorageFilePort {
    StorageReference uploadPreview(StorageOwnershipScope scope, IssuanceIdempotencyKey key, byte[] bytes, String contentType);
    StorageReference registerUpload(StorageOwnershipScope scope, String storageUri, String expectedKey, String contentType);
}
