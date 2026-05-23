package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentArtifactUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadsWhenEnabled() throws Exception {
        Path artifacts = tempDir.resolve("artifacts/job-seg/seg_0/output.mp4");
        Files.createDirectories(artifacts.getParent());
        Files.write(artifacts, new byte[] {1, 2, 3, 4});

        BlobStorage blobStorage = mock(BlobStorage.class);
        when(blobStorage.put(any(PutObjectCommand.class)))
                .thenReturn(new StorageObjectRef("localFsStorageProvider", "render-cache", "ten/segment/tl/seg_0.mp4"));

        RenderCacheProperties props = new RenderCacheProperties();
        props.setUploadEnabled(true);
        props.setRemoteEnabled(true);

        TimelineScriptParser parser = new TimelineScriptParser();
        SegmentArtifactUploadService service = new SegmentArtifactUploadService(
                blobStorage, props, parser, new RenderCacheUriResolver(props));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "storageRoot", tempDir.toString());

        String uri = "localFsStorageProvider://artifacts/job-seg/seg_0/output.mp4";
        var uploaded = service.uploadSegmentArtifact("ten", "seg_0", "segment:tl:seg_0:r1:SEGMENT", uri);
        assertTrue(uploaded.isPresent());
        assertTrue(uploaded.get().contains("render-cache"));
        verify(blobStorage).put(any(PutObjectCommand.class));
    }
}
