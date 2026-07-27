package com.example.platform.render.domain.storage.write;
import com.example.platform.render.domain.storage.digest.ContentDigest;
import com.example.platform.render.domain.storage.identity.StorageProviderId;
import com.example.platform.render.domain.storage.namespace.StorageNamespace;
import java.io.Serializable;
public record StorageWriteSession(String writeSessionId, String idempotencyKey, StorageNamespace namespace, ContentDigest expectedDigest, long expectedLength, StorageProviderId providerSelection, WriteSessionState state) implements Serializable {
    public StorageWriteSession {
        if (writeSessionId == null || writeSessionId.isBlank()) throw new IllegalArgumentException("writeSessionId required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey required");
        if (namespace == null) throw new IllegalArgumentException("namespace required");
        if (expectedDigest == null) throw new IllegalArgumentException("expectedDigest required");
        if (expectedLength < 0) throw new IllegalArgumentException("expectedLength must be >= 0");
        if (providerSelection == null) throw new IllegalArgumentException("providerSelection required");
        if (state == null) throw new IllegalArgumentException("state required");
    }
}
