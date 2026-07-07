package com.example.platform.web.render;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/preview")
@Tag(name = "Preview Media", description = "Preview/dev media upload")
public class PreviewMediaController {

    private static final Logger log = LoggerFactory.getLogger(PreviewMediaController.class);

    @PostMapping("/media")
    @Operation(summary = "Upload preview media")
    public Map<String, String> uploadPreviewMedia(@RequestParam("file") MultipartFile file) {
        if (!"video/mp4".equals(file.getContentType())) {
            throw new IllegalArgumentException("Only video/mp4 is supported");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("File too large (max 20MB)");
        }
        try {
            String mediaId = "media_" + System.currentTimeMillis();
            String objectKey = mediaId + "/input.mp4";
            java.nio.file.Path storageRoot = java.nio.file.Path.of("/tmp/platform");
            java.nio.file.Path mediaPath = storageRoot.resolve("preview-media").resolve(objectKey);
            java.nio.file.Files.createDirectories(mediaPath.getParent());
            java.nio.file.Files.write(mediaPath, file.getBytes());
            String storageUri = "localFsStorageProvider://preview-media/" + objectKey;
            return Map.of("mediaId", mediaId, "storageUri", storageUri, "size", String.valueOf(file.getSize()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to store media", e);
        }
    }
}
