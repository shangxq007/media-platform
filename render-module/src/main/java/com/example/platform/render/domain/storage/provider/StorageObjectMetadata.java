package com.example.platform.render.domain.storage.provider;

import com.example.platform.render.domain.storage.digest.ContentDigest;
import com.example.platform.render.domain.storage.identity.StorageObjectId;

/**
 * Metadata returned by stat() on a storage object.
 */
public record StorageObjectMetadata(StorageObjectId objectId, ContentDigest digest, long length) {}
