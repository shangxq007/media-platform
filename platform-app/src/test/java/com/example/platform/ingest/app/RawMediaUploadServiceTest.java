package com.example.platform.ingest.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.api.rawmedia.RawMediaProductRegistrationCommand;
import com.example.platform.render.api.rawmedia.RawMediaProductRegistrationFacade;
import com.example.platform.render.api.rawmedia.RawMediaProductRegistrationResult;
import com.example.platform.render.domain.asset.Asset;
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
    private RawMediaProductRegistrationFacade productRegistrationFacade;

    private RawMediaUploadService service;

    @BeforeEach
    void setUp() {
        service = new RawMediaUploadService(blobStorage, assetRepository, productRegistrationFacade);
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

        Instant createdAt = Instant.now();
        when(productRegistrationFacade.registerRawMedia(any()))
                .thenReturn(new RawMediaProductRegistrationResult("prod-1", createdAt));

        // Act
        RawMediaUploadResult result = service.upload(tenantId, projectId, fileBytes, filename, contentType, null);

        // Assert
        assertNotNull(result);
        assertEquals("prod-1", result.productId());
        assertEquals(createdAt, result.createdAt());

        verify(blobStorage).put(argThat(cmd ->
                cmd.bucket().equals("uploads") && cmd.content() == fileBytes && cmd.contentType().equals(contentType)));
        verify(assetRepository).register(eq(tenantId), eq(projectId), any(), eq("VIDEO"),
                eq(filename), eq(5L), isNull(), isNull(), isNull(), isNull());
        verify(productRegistrationFacade).registerRawMedia(argThat(cmd ->
                cmd.tenantId().equals(tenantId)
                        && cmd.projectId().equals(projectId)
                        && cmd.assetId().equals("asset-1")
                        && cmd.storageReferenceUri().equals("localFs://uploads/tenant/t1/...")
                        && cmd.mimeType().equals(contentType)));
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
        when(productRegistrationFacade.registerRawMedia(any()))
                .thenReturn(new RawMediaProductRegistrationResult("p1", Instant.now()));

        RawMediaUploadResult result = service.upload("t1", "p1", emptyBytes, "empty", null, null);
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
