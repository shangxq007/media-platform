package com.example.platform.ingest.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.domain.product.RepresentationKind;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.StorageObjectRef;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RawMediaUploadServiceTest {

    @Mock
    private BlobStorage blobStorage;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private ProductRuntimeService productRuntimeService;

    private RawMediaUploadService service;

    @BeforeEach
    void setUp() {
        service = new RawMediaUploadService(blobStorage, assetRepository, productRuntimeService);
    }

    @Test
    void upload_success() {
        // Arrange
        byte[] fileBytes = new byte[]{1, 2, 3, 4, 5};
        String filename = "video.mp4";
        String contentType = "video/mp4";
        String tenantId = "t1";
        String projectId = "p1";

        when(blobStorage.code()).thenReturn("localFs");
        when(blobStorage.put(any())).thenReturn(new StorageObjectRef("localFs", "uploads", "tenant/t1/..."));
        Asset mockAsset = new Asset(
                "asset-1", tenantId, projectId, "tenant/t1/workspace/default/project/p1/assets/asset-1/video.mp4",
                "VIDEO", filename, 5L, null, null, null, null,
                "v1", null, null, null, null, null, null, false, false, "DRAFT",
                Instant.now(), Instant.now());
        when(assetRepository.register(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAsset);

        Product mockProduct = new Product(
                "prod-1", tenantId, projectId, "asset-1",
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "user-upload", null, null, ProductStatus.REGISTERED,
                "localFs://uploads/tenant/t1/...", null, null, contentType, 1, null,
                Instant.now(), Instant.now());
        when(productRuntimeService.register(any())).thenReturn(mockProduct);

        // Act
        Product result = service.upload(tenantId, projectId, fileBytes, filename, contentType, null);

        // Assert
        assertNotNull(result);
        assertEquals("prod-1", result.productId());
        assertEquals(ProductType.RAW_MEDIA, result.productType());
        assertEquals(ProductStatus.REGISTERED, result.status());
        assertEquals("asset-1", result.ownerAssetId());

        verify(blobStorage).put(argThat(cmd ->
                cmd.bucket().equals("uploads") && cmd.content() == fileBytes && cmd.contentType().equals(contentType)));
        verify(assetRepository).register(eq(tenantId), eq(projectId), any(), eq("VIDEO"),
                eq(filename), eq(5L), isNull(), isNull(), isNull(), isNull());
        verify(productRuntimeService).register(argThat(p ->
                p.productType() == ProductType.RAW_MEDIA
                        && p.status() == ProductStatus.REGISTERED
                        && p.representationKind() == RepresentationKind.MEDIA_FILE
                        && "user-upload".equals(p.producerType())));
    }

    @Test
    void upload_emptyFile_stillProcesses() {
        byte[] emptyBytes = new byte[0];
        when(blobStorage.code()).thenReturn("localFs");
        when(blobStorage.put(any())).thenReturn(new StorageObjectRef("localFs", "uploads", "key"));
        Asset mockAsset = new Asset(
                "a1", "t1", "p1", "key", "UNKNOWN", "empty", 0L,
                null, null, null, null, "v1", null, null, null, null, null, null, false, false, "DRAFT",
                Instant.now(), Instant.now());
        when(assetRepository.register(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAsset);
        Product mockProduct = new Product(
                "p1", "t1", "p1", "a1", ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "user-upload", null, null, ProductStatus.REGISTERED, "ref", null, null, null, 1, null,
                Instant.now(), Instant.now());
        when(productRuntimeService.register(any())).thenReturn(mockProduct);

        Product result = service.upload("t1", "p1", emptyBytes, "empty", null, null);
        assertNotNull(result);
    }

    @Test
    void detectMediaType_fromContentType() {
        assertEquals("VIDEO", RawMediaUploadService.detectMediaType("video/mp4", null));
        assertEquals("AUDIO", RawMediaUploadService.detectMediaType("audio/wav", null));
        assertEquals("IMAGE", RawMediaUploadService.detectMediaType("image/png", null));
        assertEquals("SUBTITLE", RawMediaUploadService.detectMediaType("application/x-subrip", null));
        assertEquals("UNKNOWN", RawMediaUploadService.detectMediaType("application/octet-stream", null));
    }

    @Test
    void detectMediaType_fromFilename() {
        assertEquals("VIDEO", RawMediaUploadService.detectMediaType(null, "clip.mp4"));
        assertEquals("AUDIO", RawMediaUploadService.detectMediaType(null, "song.mp3"));
        assertEquals("IMAGE", RawMediaUploadService.detectMediaType(null, "photo.jpg"));
        assertEquals("SUBTITLE", RawMediaUploadService.detectMediaType(null, "subs.srt"));
        assertEquals("UNKNOWN", RawMediaUploadService.detectMediaType(null, "data.bin"));
    }

    @Test
    void detectMediaType_prefersContentTypeOverFilename() {
        // Content type says audio, filename says video — content type wins
        assertEquals("AUDIO", RawMediaUploadService.detectMediaType("audio/mp3", "clip.mp4"));
    }

    @Test
    void sanitizeFilename_normal() {
        assertEquals("video.mp4", RawMediaUploadService.sanitizeFilename("video.mp4"));
    }

    @Test
    void sanitizeFilename_stripsPath() {
        assertEquals("_tmp_video.mp4", RawMediaUploadService.sanitizeFilename("/tmp/video.mp4"));
        assertEquals("_tmp_video.mp4", RawMediaUploadService.sanitizeFilename("\\tmp\\video.mp4"));
    }

    @Test
    void sanitizeFilename_nullReturnsUpload() {
        assertEquals("upload", RawMediaUploadService.sanitizeFilename(null));
        assertEquals("upload", RawMediaUploadService.sanitizeFilename(""));
        assertEquals("upload", RawMediaUploadService.sanitizeFilename("   "));
    }

    @Test
    void sanitizeFilename_limitsLength() {
        String longName = "a".repeat(300) + ".mp4";
        String result = RawMediaUploadService.sanitizeFilename(longName);
        assertTrue(result.length() <= 255);
    }
}
