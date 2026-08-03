package com.example.platform.ingest.api;

import com.example.platform.ingest.api.dto.UploadRawMediaResponse;
import com.example.platform.ingest.app.RawMediaUploadService;
import com.example.platform.ingest.app.RawMediaUploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API for raw media upload.
 *
 * <p>Endpoint: {@code POST /api/v1/tenants/{tenantId}/projects/{projectId}/upload/raw-media}
 *
 * <p>Accepts {@code multipart/form-data} with:
 * <ul>
 *   <li>{@code file} (required) — the media file</li>
 *   <li>{@code displayName} (optional) — human-readable name</li>
 *   <li>{@code contentType} (optional) — declared MIME type</li>
 * </ul>
 *
 * <p>Returns: {@code { productId, status, createdAt }}
 *
 * <p>Safety: no storage references, signed URLs, or internal paths exposed.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/projects/{projectId}/upload")
@Tag(name = "Upload API", description = "Raw media upload")
public class RawMediaUploadController {

    private static final Logger log = LoggerFactory.getLogger(RawMediaUploadController.class);

    private final RawMediaUploadService uploadService;

    public RawMediaUploadController(RawMediaUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/raw-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload raw media file")
    public ResponseEntity<UploadRawMediaResponse> uploadRawMedia(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "contentType", required = false) String contentType) {

        if (file.isEmpty()) {
            log.warn("Upload rejected: empty file, tenant={} project={}", tenantId, projectId);
            return ResponseEntity.badRequest().body(UploadRawMediaResponse.failed());
        }

        // Use declared content type, fallback to file's content type
        String resolvedContentType = contentType != null && !contentType.isBlank()
                ? contentType
                : file.getContentType();

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";

        log.info("Upload received: tenant={} project={} filename={} size={} contentType={}",
                tenantId, projectId, filename, file.getSize(), resolvedContentType);

        try {
            byte[] bytes = file.getBytes();
            RawMediaUploadResult result = uploadService.upload(
                    tenantId, projectId, bytes, filename, resolvedContentType, displayName);

            log.info("Upload complete: tenant={} project={} productId={}",
                    tenantId, projectId, result.productId());
            return ResponseEntity.ok(UploadRawMediaResponse.success(result.productId(), result.createdAt()));

        } catch (IOException e) {
            log.error("Upload failed: tenant={} project={} filename={}", tenantId, projectId, filename, e);
            return ResponseEntity.internalServerError().body(UploadRawMediaResponse.failed());
        } catch (Exception e) {
            log.error("Upload processing error: tenant={} project={} filename={}", tenantId, projectId, filename, e);
            return ResponseEntity.internalServerError().body(UploadRawMediaResponse.failed());
        }
    }
}
