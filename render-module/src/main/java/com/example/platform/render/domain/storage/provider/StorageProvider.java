package com.example.platform.render.domain.storage.provider;
import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.render.domain.storage.namespace.StorageNamespace;
import com.example.platform.render.domain.storage.read.ByteRange;
import com.example.platform.render.domain.storage.read.IntegrityRequirement;
import com.example.platform.render.domain.storage.read.StorageDeletionRequest;
import com.example.platform.render.domain.storage.read.StorageDeletionResult;
import com.example.platform.render.domain.storage.read.StorageReadRequest;
import com.example.platform.render.domain.storage.write.StorageWriteSession;
import com.example.platform.render.domain.storage.write.WriteSessionResult;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;
public interface StorageProvider {
    StorageProviderId providerId();
    StorageProviderCapabilities capabilities();
    StorageWriteSession beginWrite(String writeSessionId, StorageNamespace namespace, ContentDigest expectedDigest, long expectedLength);
    void write(StorageWriteSession session, byte[] data, int offset, int length);
    WriteSessionResult completeWrite(StorageWriteSession session, ContentDigest actualDigest);
    void abortWrite(StorageWriteSession session);
    Optional<InputStream> openRead(StorageReadRequest request);
    Optional<StorageObjectMetadata> stat(StorageObjectId objectId);
    StorageReplicaId copy(StorageObjectId source, StorageObjectId target, StorageNamespace targetNamespace);
    StorageDeletionResult delete(StorageDeletionRequest request);
    HealthStatus health();
    record HealthStatus(boolean healthy, String detail) {}
}
