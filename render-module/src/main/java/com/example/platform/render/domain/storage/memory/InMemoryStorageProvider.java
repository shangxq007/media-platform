package com.example.platform.render.domain.storage.memory;
import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.render.domain.storage.namespace.StorageNamespace;
import com.example.platform.render.domain.storage.provider.*;
import com.example.platform.render.domain.storage.read.*;
import com.example.platform.render.domain.storage.write.StorageWriteSession;
import com.example.platform.render.domain.storage.write.WriteSessionResult;
import com.example.platform.render.domain.storage.write.WriteSessionState;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * In-memory storage provider for contract testing only. NOT for production use.
 */
public final class InMemoryStorageProvider implements StorageProvider {
    private final StorageProviderId providerId;
    private final StorageProviderCapabilities capabilities;
    private final Map<StorageObjectId, byte[]> store = new ConcurrentHashMap<>();
    private final Map<StorageObjectId, StorageObjectMetadata> metadata = new ConcurrentHashMap<>();
    private final Map<String, StorageWriteSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, byte[]> staging = new ConcurrentHashMap<>();
    private final Set<String> committed = ConcurrentHashMap.newKeySet();
    private final Set<StorageObjectId> deleted = ConcurrentHashMap.newKeySet();
    public InMemoryStorageProvider(StorageProviderId providerId, StorageProviderCapabilities capabilities) {
        this.providerId = providerId;
        this.capabilities = capabilities;
    }
    @Override public StorageProviderId providerId() { return providerId; }
    @Override public StorageProviderCapabilities capabilities() { return capabilities; }
    @Override public StorageWriteSession beginWrite(String writeSessionId, StorageNamespace namespace, ContentDigest expectedDigest, long expectedLength) {
        var session = new StorageWriteSession(writeSessionId, UUID.randomUUID().toString(), namespace, expectedDigest, expectedLength, providerId, WriteSessionState.PENDING);
        sessions.put(writeSessionId, session);
        return session;
    }
    @Override public void write(StorageWriteSession session, byte[] data, int offset, int length) {
        staging.compute(session.writeSessionId(), (k, v) -> {
            byte[] base = v != null ? v : new byte[0];
            byte[] combined = new byte[base.length + length];
            System.arraycopy(base, 0, combined, 0, base.length);
            System.arraycopy(data, offset, combined, base.length, length);
            return combined;
        });
    }
    @Override public WriteSessionResult completeWrite(StorageWriteSession session, ContentDigest actualDigest) {
        String sid = session.writeSessionId();
        if (committed.contains(sid)) {
            return new WriteSessionResult(new StorageReplicaId(sid), true, session.idempotencyKey());
        }
        committed.add(sid);
        byte[] data = staging.getOrDefault(sid, new byte[0]);
        var objId = new StorageObjectId("obj-" + sid);
        store.put(objId, data);
        metadata.put(objId, new StorageObjectMetadata(objId, actualDigest, data.length));
        return new WriteSessionResult(new StorageReplicaId(sid), false, session.idempotencyKey());
    }
    @Override public void abortWrite(StorageWriteSession session) {
        staging.remove(session.writeSessionId());
        committed.remove(session.writeSessionId());
    }
    @Override public Optional<InputStream> openRead(StorageReadRequest request) {
        byte[] data = store.get(request.objectId());
        if (data == null) return Optional.empty();
        return Optional.of(new ByteArrayInputStream(data));
    }
    @Override public Optional<StorageObjectMetadata> stat(StorageObjectId objectId) { return Optional.ofNullable(metadata.get(objectId)); }
    @Override public StorageReplicaId copy(StorageObjectId source, StorageObjectId target, StorageNamespace targetNamespace) {
        byte[] data = store.get(source);
        if (data != null) {
            byte[] copy = Arrays.copyOf(data, data.length);
            store.put(target, copy);
            StorageObjectMetadata md = metadata.get(source);
            if (md != null) metadata.put(target, new StorageObjectMetadata(target, md.digest(), md.length()));
        }
        return new StorageReplicaId("replica-" + target.value());
    }
    @Override public StorageDeletionResult delete(StorageDeletionRequest request) {
        boolean existed = store.containsKey(request.objectId());
        boolean alreadyDeleted = deleted.contains(request.objectId());
        store.remove(request.objectId());
        metadata.remove(request.objectId());
        deleted.add(request.objectId());
        return new StorageDeletionResult(request.objectId(), existed, alreadyDeleted);
    }
    @Override public HealthStatus health() { return new HealthStatus(true, "healthy"); }
}
