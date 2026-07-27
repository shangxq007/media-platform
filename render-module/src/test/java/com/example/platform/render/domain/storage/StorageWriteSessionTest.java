package com.example.platform.render.domain.storage;
import com.example.platform.render.domain.storage.write.*;
import com.example.platform.render.domain.storage.digest.*;
import com.example.platform.render.domain.storage.identity.*;
import com.example.platform.render.domain.storage.namespace.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageWriteSessionTest {
    @Test void validSession() {
        ContentDigest digest = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageWriteSession session = new StorageWriteSession("ws-1", "idem-1", ns, digest, 1000, new StorageProviderId("s3"), WriteSessionState.PENDING);
        assertEquals("ws-1", session.writeSessionId());
        assertEquals(1000, session.expectedLength());
    }
    @Test void writeSessionId_blankRejected() {
        ContentDigest digest = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        assertThrows(IllegalArgumentException.class, () -> new StorageWriteSession("", "idem-1", ns, digest, 1000, new StorageProviderId("s3"), WriteSessionState.PENDING));
    }
    @Test void expectedLength_negativeRejected() {
        ContentDigest digest = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        assertThrows(IllegalArgumentException.class, () -> new StorageWriteSession("ws-1", "idem-1", ns, digest, -1, new StorageProviderId("s3"), WriteSessionState.PENDING));
    }
}
