import com.example.platform.storage.contract.memory.*;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.namespace.*;
import com.example.platform.storage.contract.provider.*;
import com.example.platform.storage.contract.read.*;
import com.example.platform.storage.contract.write.*;
import com.example.platform.shared.digest.ContentDigest;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryProviderTest {
    private InMemoryStorageProvider createProvider() {
        StorageProviderId pid = new StorageProviderId("mem");
        Map<ProviderCapability, CapabilitySupport> caps = Map.of(
            ProviderCapability.RANGE_READ, CapabilitySupport.SUPPORTED,
            ProviderCapability.STREAMING_READ, CapabilitySupport.SUPPORTED
        );
        StorageProviderCapabilities sc = new StorageProviderCapabilities(pid, caps);
        return new InMemoryStorageProvider(pid, sc);
    }
    @Test void beginAndCompleteWrite() {
        InMemoryStorageProvider provider = createProvider();
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        ContentDigest digest = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        StorageWriteSession session = provider.beginWrite("ws-1", ns, digest, 10);
        provider.write(session, "HelloWorld".getBytes(), 0, 10);
        WriteSessionResult result = provider.completeWrite(session, digest);
        assertNotNull(result);
        assertFalse(result.alreadyCommitted());
    }
    @Test void duplicateCommit_returnsSame() {
        InMemoryStorageProvider provider = createProvider();
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        ContentDigest digest = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        StorageWriteSession session = provider.beginWrite("ws-1", ns, digest, 10);
        provider.write(session, "HelloWorld".getBytes(), 0, 10);
        WriteSessionResult r1 = provider.completeWrite(session, digest);
        WriteSessionResult r2 = provider.completeWrite(session, digest);
        assertTrue(r2.alreadyCommitted());
    }
    @Test void openRead_returnsData() throws Exception {
        InMemoryStorageProvider provider = createProvider();
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        ContentDigest digest = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        StorageWriteSession session = provider.beginWrite("ws-1", ns, digest, 10);
        provider.write(session, "HelloWorld".getBytes(), 0, 10);
        provider.completeWrite(session, digest);
        StorageObjectId objId = new StorageObjectId("obj-ws-1");
        Optional<InputStream> in = provider.openRead(new StorageReadRequest(objId, Optional.empty(), IntegrityRequirement.NONE));
        assertTrue(in.isPresent());
        byte[] data = in.get().readAllBytes();
        assertEquals("HelloWorld", new String(data));
    }
    @Test void abortWrite_allowsNewAttempt() {
        InMemoryStorageProvider provider = createProvider();
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        ContentDigest digest = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        StorageWriteSession session = provider.beginWrite("ws-1", ns, digest, 10);
        provider.write(session, "HelloWorld".getBytes(), 0, 10);
        provider.abortWrite(session);
        StorageWriteSession session2 = provider.beginWrite("ws-2", ns, digest, 10);
        assertNotNull(session2);
    }
    @Test void delete_isIdempotent() {
        InMemoryStorageProvider provider = createProvider();
        StorageObjectId objId = new StorageObjectId("obj-1");
        StorageDeletionResult r1 = provider.delete(new StorageDeletionRequest(objId, false));
        assertFalse(r1.deleted());
        assertFalse(r1.alreadyDeleted());
        StorageDeletionResult r2 = provider.delete(new StorageDeletionRequest(objId, false));
        assertFalse(r2.deleted());
        assertTrue(r2.alreadyDeleted());
    }
    @Test void health_returnsHealthy() {
        InMemoryStorageProvider provider = createProvider();
        assertTrue(provider.health().healthy());
    }
    @Test void stat_returnsMetadata() {
        InMemoryStorageProvider provider = createProvider();
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        ContentDigest digest = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        StorageWriteSession session = provider.beginWrite("ws-1", ns, digest, 10);
        provider.write(session, "HelloWorld".getBytes(), 0, 10);
        provider.completeWrite(session, digest);
        StorageObjectId objId = new StorageObjectId("obj-ws-1");
        Optional<StorageObjectMetadata> stat = provider.stat(objId);
        assertTrue(stat.isPresent());
        assertEquals(10, stat.get().length());
    }
}
