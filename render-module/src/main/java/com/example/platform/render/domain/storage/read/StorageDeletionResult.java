package com.example.platform.render.domain.storage.read;
import com.example.platform.render.domain.storage.identity.StorageObjectId;
public record StorageDeletionResult(StorageObjectId objectId, boolean deleted, boolean alreadyDeleted) {}
