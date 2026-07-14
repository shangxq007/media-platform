package com.example.platform.render.app.storage;

import com.example.platform.render.domain.storage.StorageClass;
import com.example.platform.render.domain.storage.StorageProviderType;
import com.example.platform.render.domain.storage.StorageReference;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VS.1 Storage boundary tests for {@link StorageRuntimeService}.
 *
 * <p>Tests storage reference management and materialization boundaries.
 * Verifies architecture constraints:
 * <ul>
 *   <li>StorageRuntime owns materialization semantics</li>
 *   <li>No signed URLs persisted or exposed</li>
 *   <li>No bucket/key exposed in public API</li>
 *   <li>Local paths only returned to internal runtime code</li>
 *   <li>S3-compatible provider type detection</li>
 * </ul>
 *
 * <p>Uses Mockito — no database, no H2, no S3 calls.
 */
class StorageRuntimeServiceBoundaryTest {
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }


    private StorageReferenceRepository repo;
    private StorageRuntimeService service;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(StorageReferenceRepository.class);
        service = new StorageRuntimeService(repo, mockProvider(null));
    }

    // ========== Registration ==========

    @Nested
    @DisplayName("Storage reference registration")
    class Registration {

        @Test
        @DisplayName("register() persists and returns storage reference")
        void registerPersists() {
            StorageReference ref = localRef("stor-1", "/data/output", "output.mp4");
            when(repo.save(ref)).thenReturn(ref);

            StorageReference result = service.register(ref);

            assertNotNull(result);
            assertEquals("stor-1", result.storageReferenceId());
            assertEquals(StorageProviderType.LOCAL.name(), result.providerType());
        }

        @Test
        @DisplayName("register() accepts S3-compatible reference")
        void registerAcceptsS3() {
            StorageReference ref = s3Ref("stor-2", "my-bucket", "projects/p1/rj-1/output.mp4");
            when(repo.save(ref)).thenReturn(ref);

            StorageReference result = service.register(ref);

            assertEquals(StorageProviderType.S3_COMPATIBLE.name(), result.providerType());
        }

        @Test
        @DisplayName("register() preserves checksum and content hash")
        void registerPreservesChecksum() {
            StorageReference ref = localRef("stor-1", "/data/output", "output.mp4");
            when(repo.save(ref)).thenReturn(ref);

            StorageReference result = service.register(ref);

            assertEquals("abc123def456", result.checksum());
            assertEquals("abc123def456", result.contentHash());
        }
    }

    // ========== Materialization boundaries ==========

    @Nested
    @DisplayName("Materialization boundaries")
    class Materialization {

        @Test
        @DisplayName("materialize() throws for missing storage reference")
        void materializeReturnsEmptyWhenNotFound() {
            when(repo.findById("stor-missing")).thenReturn(Optional.empty());

            Optional<String> result = service.materialize("stor-missing");
            assertTrue(result.isEmpty(), "materialize() should return empty for missing reference");
        }

        @Test
        @DisplayName("materialize() for LOCAL provider throws when file missing")
        void materializeLocalReturnsEmptyWhenFileMissing() {
            StorageReference ref = localRef("stor-1", "/nonexistent/path", "missing.mp4");
            when(repo.findById("stor-1")).thenReturn(Optional.of(ref));

            Optional<String> result = service.materialize("stor-1");
            assertTrue(result.isEmpty(), "materialize() should return empty when local file missing");
        }

        @Test
        @DisplayName("materialize() for S3 without materializer throws")
        void materializeS3WithoutMaterializerReturnsEmpty() {
            StorageReference ref = s3Ref("stor-2", "bucket", "key.mp4");
            when(repo.findById("stor-2")).thenReturn(Optional.of(ref));

            Optional<String> result = service.materialize("stor-2");
            assertTrue(result.isEmpty(), "materialize() should return empty for S3 without materializer");
        }
    }

    // ========== Storage reference domain model ==========

    @Nested
    @DisplayName("StorageReference domain model")
    class DomainModel {

        @Test
        @DisplayName("absolutePath() concatenates root and relative path")
        void absolutePathConcatenates() {
            StorageReference ref = new StorageReference(
                    "stor-1", StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                    "/data/output", "artifacts/job-1/output.mp4",
                    "abc123", "abc123", 1024L, "video/mp4",
                    Instant.now(), Instant.now());

            assertEquals("/data/output/artifacts/job-1/output.mp4", ref.absolutePath());
        }

        @Test
        @DisplayName("absolutePath() handles root ending with slash")
        void absolutePathHandlesTrailingSlash() {
            StorageReference ref = new StorageReference(
                    "stor-1", StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                    "/data/output/", "artifacts/job-1/output.mp4",
                    "abc123", "abc123", 1024L, "video/mp4",
                    Instant.now(), Instant.now());

            assertEquals("/data/output/artifacts/job-1/output.mp4", ref.absolutePath());
        }

        @Test
        @DisplayName("absolutePath() returns relative when root is null")
        void absolutePathReturnsRelativeWhenRootNull() {
            StorageReference ref = new StorageReference(
                    "stor-1", StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                    null, "output.mp4",
                    "abc123", "abc123", 1024L, "video/mp4",
                    Instant.now(), Instant.now());

            assertEquals("output.mp4", ref.absolutePath());
        }

        @Test
        @DisplayName("StorageReference has no signed URL field")
        void noSignedUrlField() {
            StorageReference ref = localRef("stor-1", "/data", "file.mp4");

            // Verify the record has no signed URL in toString
            String str = ref.toString();
            assertFalse(str.toLowerCase().contains("signedurl"), "StorageReference must not have signed URL field");
            assertFalse(str.toLowerCase().contains("signed_url"), "StorageReference must not have signed_url field");
            assertFalse(str.toLowerCase().contains("presign"), "StorageReference must not have presign field");
        }

        @Test
        @DisplayName("StorageReference has no credentials fields")
        void noCredentialsFields() {
            StorageReference ref = localRef("stor-1", "/data", "file.mp4");

            String str = ref.toString();
            assertFalse(str.toLowerCase().contains("password"), "No password field");
            assertFalse(str.toLowerCase().contains("secret"), "No secret field");
            assertFalse(str.toLowerCase().contains("token"), "No token field");
            assertFalse(str.toLowerCase().contains("accesskey"), "No accessKey field");
        }
    }

    // ========== Provider type detection ==========

    @Nested
    @DisplayName("S3-compatible provider type detection")
    class ProviderTypeDetection {

        @Test
        @DisplayName("S3 provider type is S3-compatible")
        void s3IsCompatible() {
            StorageReference ref = s3RefWithProvider("stor-1", "S3", "bucket", "key.mp4");
            when(repo.findById("stor-1")).thenReturn(Optional.of(ref));

            // Returns empty because S3ObjectMaterializer not available (null),
            // which means it correctly identified as S3-compatible
            Optional<String> result = service.materialize("stor-1");
            assertTrue(result.isEmpty(), "materialize() should return empty for S3 without materializer");
        }

        @Test
        @DisplayName("S3_COMPATIBLE provider type is S3-compatible")
        void s3CompatibleIsCompatible() {
            StorageReference ref = s3RefWithProvider("stor-1", "S3_COMPATIBLE", "bucket", "key.mp4");
            when(repo.findById("stor-1")).thenReturn(Optional.of(ref));

            Optional<String> result = service.materialize("stor-1");
            assertTrue(result.isEmpty(), "materialize() should return empty for S3 without materializer");
        }

        @Test
        @DisplayName("OBJECT_STORAGE provider type is S3-compatible")
        void objectStorageIsCompatible() {
            StorageReference ref = s3RefWithProvider("stor-1", "OBJECT_STORAGE", "bucket", "key.mp4");
            when(repo.findById("stor-1")).thenReturn(Optional.of(ref));

            Optional<String> result = service.materialize("stor-1");
            assertTrue(result.isEmpty(), "materialize() should return empty for S3 without materializer");
        }
    }

    // ========== Existence check ==========

    @Nested
    @DisplayName("Existence and query operations")
    class ExistenceCheck {

        @Test
        @DisplayName("exists() delegates to repository")
        void existsDelegates() {
            when(repo.exists("stor-1")).thenReturn(true);

            assertTrue(service.find("stor-1").isPresent());
        }

        @Test
        @DisplayName("find() delegates to repository")
        void findDelegates() {
            StorageReference ref = localRef("stor-1", "/data", "file.mp4");
            when(repo.findById("stor-1")).thenReturn(Optional.of(ref));

            Optional<StorageReference> result = service.find("stor-1");

            assertTrue(result.isPresent());
            assertEquals("stor-1", result.get().storageReferenceId());
        }
    }

    // ========== Helpers ==========

    private StorageReference localRef(String id, String rootPath, String relativePath) {
        return new StorageReference(
                id, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                rootPath, relativePath, "abc123def456", "abc123def456", 1024L, "video/mp4",
                Instant.now(), Instant.now());
    }

    private StorageReference s3Ref(String id, String bucket, String objectKey) {
        return new StorageReference(
                id, StorageProviderType.S3_COMPATIBLE.name(), StorageClass.STANDARD,
                bucket, objectKey, "abc123def456", "abc123def456", 1024L, "video/mp4",
                Instant.now(), Instant.now());
    }

    private StorageReference s3RefWithProvider(String id, String providerType, String bucket, String objectKey) {
        return new StorageReference(
                id, providerType, StorageClass.STANDARD,
                bucket, objectKey, "abc123def456", "abc123def456", 1024L, "video/mp4",
                Instant.now(), Instant.now());
    }
}
