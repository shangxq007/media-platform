package com.example.platform.render.domain.storage.provider;

import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;

/**
 * Metadata returned by stat() on a storage object.
 */
public record StorageObjectMetadata(StorageObjectId objectId, ContentDigest digest, long length) {}
