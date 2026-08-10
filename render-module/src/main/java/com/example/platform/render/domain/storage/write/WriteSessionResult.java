package com.example.platform.render.domain.storage.write;

import com.example.platform.storage.contract.StorageReplicaId;

public record WriteSessionResult(StorageReplicaId replicaId, boolean alreadyCommitted, String idempotencyKey) {}
