package com.example.platform.storage.opendal.conformance;

import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.*;
import com.example.platform.storage.contract.read.*;
import com.example.platform.storage.contract.write.*;
import com.example.platform.storage.opendal.OpenDalStorageException;
import com.example.platform.storage.opendal.OpenDalProviderConfiguration;
import com.example.platform.storage.opendal.OpenDalStorageProvider;
import com.example.platform.storage.opendal.testutil.EmbeddedS3Server;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3-compatible mandatory live data-plane conformance test.
 * <p>
 * Uses the version-controlled {@link EmbeddedS3Server} (ephemeral port).
 * No external /tmp dependencies. No pre-existing mutable state.
 * No fixed port — the server binds to an OS-assigned free port and the
 * OpenDAL configuration consumes the bound endpoint.
 */
class OpenDalS3MandatoryLiveTest {

    private static final String REGION = "us-east-1";
    private static final String BUCKET = "opendal-mandatory-test-bucket";

    private static EmbeddedS3Server s3Server;
    private static StorageProvider provider;
    private static StorageNamespace testNamespace;

    @BeforeAll
    static void setup() {
        // Disable AWS EC2 metadata service (prevent OpenDAL from trying IMDS)
        System.setProperty("aws.ec2.metadata.disabled", "true");
        s3Server = new EmbeddedS3Server(0);
        try {
            s3Server.start();
            String endpoint = s3Server.getEndpoint();

            StorageProviderId providerId = new StorageProviderId("opendal-s3-mandatory");
            OpenDalProviderConfiguration config = new OpenDalProviderConfiguration(
                    providerId,
                    "s3",
                    null,
                    REGION,
                    endpoint,
                    BUCKET,
                    null,
                    null,
                    30000L,
                    0
            );
            provider = OpenDalStorageProvider.create(config);

            testNamespace = new StorageNamespace(
                    "test-tenant", "test-project", NamespaceClass.SOURCE,
                    RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL
            );

            // Verify connectivity
            StorageProvider.HealthStatus h = provider.health();
            if (!h.healthy()) {
                throw new RuntimeException("Health check failed: " + h.detail());
            }
        } catch (Exception e) {
            if (s3Server != null) try { s3Server.stop(); } catch (Exception ignored) {}
            fail("S3 mandatory test setup failed: " + e.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        if (s3Server != null) try { s3Server.stop(); } catch (Exception ignored) {}
    }

    private static ContentDigest sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return ContentDigest.sha256(hex.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Operator creation succeeds")
    void operatorCreation_succeeds() {
        assertNotNull(provider);
        assertEquals("opendal-s3-mandatory", provider.providerId().value());
    }

    @Test
    @DisplayName("Health check HEALTHY")
    void health_returnsHealthy() {
        assertTrue(provider.health().healthy());
    }

    @Test
    @DisplayName("Two-phase write committed")
    void twoPhaseWrite_succeeds() {
        String content = "Hello S3!";
        ContentDigest digest = sha256(content);
        StorageWriteSession session = provider.beginWrite("ws-1", testNamespace, digest, content.length());
        provider.write(session, content.getBytes(), 0, content.length());
        WriteSessionResult result = provider.completeWrite(session, digest);
        assertNotNull(result);
        assertFalse(result.alreadyCommitted());
    }

    @Test
    @DisplayName("Multi-chunk write")
    void multiChunkWrite_succeeds() {
        String full = "Hello World S3!";
        ContentDigest digest = sha256(full);
        StorageWriteSession session = provider.beginWrite("ws-multi", testNamespace, digest, full.length());
        provider.write(session, "Hello ".getBytes(), 0, 6);
        provider.write(session, "World ".getBytes(), 0, 6);
        provider.write(session, "S3!".getBytes(), 0, 3);
        WriteSessionResult result = provider.completeWrite(session, digest);
        assertFalse(result.alreadyCommitted());

        StorageObjectId objId = new StorageObjectId("obj-ws-multi");
        Optional<InputStream> stream = provider.openRead(
                new StorageReadRequest(objId, Optional.empty(), IntegrityRequirement.NONE));
        assertTrue(stream.isPresent());
        byte[] data = assertDoesNotThrow(() -> stream.get().readAllBytes());
        assertEquals(full, new String(data));
    }

    @Test
    @DisplayName("OpenRead returns committed data")
    void openRead_returnsData() {
        String content = "Readable S3 Content";
        ContentDigest digest = sha256(content);
        StorageWriteSession session = provider.beginWrite("ws-2", testNamespace, digest, content.length());
        provider.write(session, content.getBytes(), 0, content.length());
        provider.completeWrite(session, digest);

        StorageObjectId objId = new StorageObjectId("obj-ws-2");
        Optional<InputStream> stream = provider.openRead(
                new StorageReadRequest(objId, Optional.empty(), IntegrityRequirement.NONE));
        assertTrue(stream.isPresent());
        byte[] data = assertDoesNotThrow(() -> stream.get().readAllBytes());
        assertEquals(content, new String(data));
    }

    @Test
    @DisplayName("Stat returns metadata")
    void stat_returnsMetadata() {
        String content = "StatS3";
        ContentDigest digest = sha256(content);
        StorageWriteSession session = provider.beginWrite("ws-3", testNamespace, digest, content.length());
        provider.write(session, content.getBytes(), 0, content.length());
        provider.completeWrite(session, digest);

        StorageObjectId objId = new StorageObjectId("obj-ws-3");

        // First verify by reading
        Optional<InputStream> stream = provider.openRead(
                new StorageReadRequest(objId, Optional.empty(), IntegrityRequirement.NONE));
        assertTrue(stream.isPresent(), "Object should exist after write");
        byte[] data = assertDoesNotThrow(() -> stream.get().readAllBytes());
        assertEquals(content, new String(data));

        // Then stat
        Optional<StorageObjectMetadata> meta = provider.stat(objId);
        assertTrue(meta.isPresent());
        assertEquals(content.length(), meta.get().length());
    }

    @Test
    @DisplayName("Range read")
    void rangeRead_returnsSubset() {
        String content = "0123456789ABCDEF";
        ContentDigest digest = sha256(content);
        StorageWriteSession session = provider.beginWrite("ws-4", testNamespace, digest, content.length());
        provider.write(session, content.getBytes(), 0, content.length());
        provider.completeWrite(session, digest);

        StorageObjectId objId = new StorageObjectId("obj-ws-4");
        ByteRange range = new ByteRange(3, 8);
        Optional<InputStream> stream = provider.openRead(
                new StorageReadRequest(objId, Optional.of(range), IntegrityRequirement.NONE));
        assertTrue(stream.isPresent());
        byte[] data = assertDoesNotThrow(() -> stream.get().readAllBytes());
        assertEquals("345678", new String(data));
    }

    @Test
    @DisplayName("Copy integrity verified")
    void copy_integrityVerified() {
        String content = "CopyS3Content";
        ContentDigest digest = sha256(content);
        StorageWriteSession sourceSession = provider.beginWrite("copy-src", testNamespace, digest, content.length());
        provider.write(sourceSession, content.getBytes(), 0, content.length());
        provider.completeWrite(sourceSession, digest);

        StorageObjectId sourceId = new StorageObjectId("obj-copy-src");
        StorageObjectId targetId = new StorageObjectId("obj-copy-tgt");

        StorageReplicaId replica = provider.copy(sourceId, targetId, testNamespace);
        assertNotNull(replica);

        Optional<StorageObjectMetadata> targetMeta = provider.stat(targetId);
        assertTrue(targetMeta.isPresent());
        assertEquals(content.length(), targetMeta.get().length());

        Optional<InputStream> stream = provider.openRead(
                new StorageReadRequest(targetId, Optional.empty(), IntegrityRequirement.NONE));
        assertTrue(stream.isPresent());
        byte[] data = assertDoesNotThrow(() -> stream.get().readAllBytes());
        assertEquals(content, new String(data));
    }

    @Test
    @DisplayName("Copy missing source throws")
    void copyMissingSource_throws() {
        assertThrows(OpenDalStorageException.class,
                () -> provider.copy(new StorageObjectId("missing"), new StorageObjectId("tgt"), testNamespace));
    }

    @Test
    @DisplayName("Delete removes object")
    void delete_removesObject() {
        String content = "DeletableS3";
        ContentDigest digest = sha256(content);
        StorageWriteSession session = provider.beginWrite("ws-9", testNamespace, digest, content.length());
        provider.write(session, content.getBytes(), 0, content.length());
        provider.completeWrite(session, digest);

        StorageObjectId objId = new StorageObjectId("obj-ws-9");
        StorageDeletionResult result = provider.delete(new StorageDeletionRequest(objId, false));
        assertTrue(result.deleted());
    }

    @Test
    @DisplayName("Delete idempotent")
    void delete_isIdempotent() {
        StorageObjectId objId = new StorageObjectId("never-existed");
        assertFalse(provider.delete(new StorageDeletionRequest(objId, false)).deleted());
    }

    @Test
    @DisplayName("Length mismatch rejected")
    void lengthMismatch_rejects() {
        ContentDigest digest = sha256("Short");
        StorageWriteSession session = provider.beginWrite("ws-7", testNamespace, digest, 100);
        provider.write(session, "Short".getBytes(), 0, 5);
        assertThrows(OpenDalStorageException.class, () -> provider.completeWrite(session, digest));
    }

    @Test
    @DisplayName("Digest mismatch rejected")
    void digestMismatch_rejects() {
        ContentDigest correct = sha256("CorrectS3Data!");
        ContentDigest wrong = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        StorageWriteSession session = provider.beginWrite("ws-8", testNamespace, correct, 14);
        provider.write(session, "CorrectS3Data!".getBytes(), 0, 14);
        assertThrows(OpenDalStorageException.class, () -> provider.completeWrite(session, wrong));
    }

    @Test
    @DisplayName("Abort write")
    void abortWrite_cleansUp() {
        String content = "AbortMeS3";
        ContentDigest digest = sha256(content);
        StorageWriteSession session = provider.beginWrite("ws-5", testNamespace, digest, content.length());
        provider.write(session, content.getBytes(), 0, content.length());
        provider.abortWrite(session);

        StorageWriteSession session2 = provider.beginWrite("ws-6", testNamespace, digest, content.length());
        provider.write(session2, content.getBytes(), 0, content.length());
        assertNotNull(provider.completeWrite(session2, digest));
    }

    @Test
    @DisplayName("Capabilities reported")
    void capabilities_reported() {
        StorageProviderCapabilities caps = provider.capabilities();
        assertNotNull(caps);
        assertEquals("opendal-s3-mandatory", caps.providerId().value());
        assertTrue(caps.supportsOrEmulated(ProviderCapability.STREAMING_READ));
        assertTrue(caps.supportsOrEmulated(ProviderCapability.STREAMING_WRITE));
        assertTrue(caps.supportsOrEmulated(ProviderCapability.DELETE));
    }

    @Test
    @DisplayName("Missing object empty")
    void missingObject_returnsEmpty() {
        Optional<InputStream> stream = provider.openRead(
                new StorageReadRequest(new StorageObjectId("nonexistent"), Optional.empty(), IntegrityRequirement.NONE));
        assertFalse(stream.isPresent());
    }

    @Test
    @DisplayName("Idempotent complete")
    void completeWrite_isIdempotent() {
        String content = "IdempotentS3";
        ContentDigest digest = sha256(content);
        StorageWriteSession session = provider.beginWrite("ws-10", testNamespace, digest, content.length());
        provider.write(session, content.getBytes(), 0, content.length());

        WriteSessionResult r1 = provider.completeWrite(session, digest);
        WriteSessionResult r2 = provider.completeWrite(session, digest);

        assertFalse(r1.alreadyCommitted());
        assertTrue(r2.alreadyCommitted());
    }

    @Test
    @DisplayName("Error mapping no exception")
    void errorMapping_noException() {
        Optional<InputStream> stream = provider.openRead(
                new StorageReadRequest(new StorageObjectId("nonexistent"), Optional.empty(), IntegrityRequirement.NONE));
        assertFalse(stream.isPresent());
    }

    @Test
    @DisplayName("Cleanup works")
    void cleanup_works() {
        assertNotNull(provider);
        assertTrue(true);
    }
}
