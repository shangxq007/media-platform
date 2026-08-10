package com.example.platform.storage.contract.read;
import com.example.platform.storage.contract.StorageObjectId;
public record StorageDeletionResult(StorageObjectId objectId, boolean deleted, boolean alreadyDeleted) {}
