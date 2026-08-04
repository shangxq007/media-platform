package com.example.platform.render.api.rawmedia;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.domain.product.RepresentationKind;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.testsupport.fakes.FakeProductDependencyRepository;
import com.example.platform.render.testsupport.fakes.FakeProductRepository;
import com.example.platform.render.testsupport.fakes.FakeStorageReferenceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class RawMediaProductRegistrationFacadeTest {

    @TempDir
    Path storageRoot;

    private FakeProductRepository productRepository;
    private FakeStorageReferenceRepository storageReferenceRepository;
    private FakeAssetRepository assetRepository;
    private RawMediaProductRegistrationFacade facade;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        storageReferenceRepository = new FakeStorageReferenceRepository();
        assetRepository = new FakeAssetRepository();
        ProductRuntimeService productRuntime = new ProductRuntimeService(
                productRepository, new FakeProductDependencyRepository());
        StorageRuntimeService storageRuntime = new StorageRuntimeService(storageReferenceRepository, mockProvider(null));
        facade = new RawMediaProductRegistrationFacade(productRuntime, storageRuntime, assetRepository, storageRoot);
    }

    @Test
    void registerRawMediaCreatesReadyRawMediaProductWithRegisteredStorageReference() throws Exception {
        writeUpload("uploads/key.mp4", "video-bytes".getBytes());
        assetRepository.asset = asset("t1", "p1", "asset-1", "uploads/key.mp4");

        RawMediaProductRegistrationResult result = facade.registerRawMedia(
                new RawMediaProductRegistrationCommand("t1", "p1", "asset-1",
                        "localFsStorageProvider://uploads/uploads/key.mp4", "video/mp4"));

        Product product = productRepository.findById(result.productId()).orElseThrow();
        assertEquals(ProductStatus.READY, product.status());
        assertEquals(ProductType.RAW_MEDIA, product.productType());
        assertEquals(RepresentationKind.MEDIA_FILE, product.representationKind());
        assertEquals("asset-1", product.ownerAssetId());
        assertEquals("video/mp4", product.mimeType());
        assertNotNull(product.storageReferenceId());
        assertTrue(storageReferenceRepository.findById(product.storageReferenceId()).isPresent());
        assertEquals(64, product.checksum().length());
    }

    @Test
    void registerRawMediaRejectsMissingAsset() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                facade.registerRawMedia(new RawMediaProductRegistrationCommand("t1", "p1", "missing",
                        "localFsStorageProvider://uploads/uploads/key.mp4", "video/mp4")));
        assertTrue(ex.getMessage().contains("asset not found"));
    }

    @Test
    void registerRawMediaRejectsProjectMismatch() throws Exception {
        writeUpload("uploads/key.mp4", "video-bytes".getBytes());
        assetRepository.asset = asset("t1", "other-project", "asset-1", "uploads/key.mp4");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                facade.registerRawMedia(new RawMediaProductRegistrationCommand("t1", "p1", "asset-1",
                        "localFsStorageProvider://uploads/uploads/key.mp4", "video/mp4")));
        assertTrue(ex.getMessage().contains("project mismatch"));
    }

    @Test
    void registerRawMediaRejectsStorageKeyMismatch() throws Exception {
        writeUpload("uploads/key.mp4", "video-bytes".getBytes());
        assetRepository.asset = asset("t1", "p1", "asset-1", "uploads/other.mp4");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                facade.registerRawMedia(new RawMediaProductRegistrationCommand("t1", "p1", "asset-1",
                        "localFsStorageProvider://uploads/uploads/key.mp4", "video/mp4")));
        assertTrue(ex.getMessage().contains("storage key mismatch"));
    }

    @Test
    void registerRawMediaRejectsMissingUploadedObject() {
        assetRepository.asset = asset("t1", "p1", "asset-1", "uploads/missing.mp4");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                facade.registerRawMedia(new RawMediaProductRegistrationCommand("t1", "p1", "asset-1",
                        "localFsStorageProvider://uploads/uploads/missing.mp4", "video/mp4")));
        assertTrue(ex.getMessage().contains("uploaded object not found"));
    }

    @Test
    void productRuntimeRemainsAuthoritativeLifecycleOwnerForAlreadyReadyProduct() throws Exception {
        writeUpload("uploads/key.mp4", "video-bytes".getBytes());
        assetRepository.asset = asset("t1", "p1", "asset-1", "uploads/key.mp4");

        RawMediaProductRegistrationResult first = facade.registerRawMedia(
                new RawMediaProductRegistrationCommand("t1", "p1", "asset-1",
                        "localFsStorageProvider://uploads/uploads/key.mp4", "video/mp4"));
        Product ready = productRepository.findById(first.productId()).orElseThrow();

        ProductRuntimeService productRuntime = new ProductRuntimeService(
                productRepository, new FakeProductDependencyRepository());
        Product again = productRuntime.markReady(ready.productId());

        assertEquals(ProductStatus.READY, again.status());
        assertEquals(ready.productId(), again.productId());
    }

    private void writeUpload(String objectKey, byte[] bytes) throws Exception {
        Path file = storageRoot.resolve("uploads").resolve(objectKey);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static Asset asset(String tenantId, String projectId, String assetId, String storageKey) {
        Instant now = Instant.now();
        return new Asset(assetId, tenantId, projectId, storageKey, "VIDEO", "video.mp4",
                11L, null, null, null, null, "v1", null, null, null, null,
                null, null, false, false, "DRAFT", now, now);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockProvider(T instance) {
        ObjectProvider<T> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    static class FakeAssetRepository extends AssetRepository {
        Asset asset;

        FakeAssetRepository() {
            super(null);
        }

        @Override
        public Optional<Asset> findById(String tenantId, String assetId) {
            if (asset != null && asset.tenantId().equals(tenantId) && asset.id().equals(assetId)) {
                return Optional.of(asset);
            }
            return Optional.empty();
        }
    }
}
