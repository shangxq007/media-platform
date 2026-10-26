package com.example.platform.storage.api;

import com.example.platform.storage.contract.*;
import com.example.platform.shared.digest.ContentDigest;

/** Read exact committed identity with bounded allocation and digest verification before disclosure. */
public interface VerifiedStorageRead {
    byte[] read(StorageProviderId provider,StorageObjectId object,ContentDigest digest,long length,long limit);
}
