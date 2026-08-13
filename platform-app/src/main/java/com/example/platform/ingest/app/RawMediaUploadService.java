package com.example.platform.ingest.app;

import com.example.platform.render.api.rawmedia.RawMediaProductRegistrationCommand;
import com.example.platform.render.api.rawmedia.RawMediaProductRegistrationFacade;
import com.example.platform.render.api.rawmedia.RawMediaProductRegistrationResult;
import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.shared.Ids;
import com.example.platform.storage.contract.StorageKeyPolicy;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates raw media upload: file → BlobStorage → Asset → RAW_MEDIA Product.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Generate storage key via {@link StorageKeyPolicy}</li>
 *   <li>Write bytes to {@link BlobStorage}</li>
 *   <li>Register {@link Asset} in asset table</li>
 *   <li>Register RAW_MEDIA product through the render API boundary</li>
 * </ol>
 *
 * <p>Safety: no signed URLs, no storage internals, no local paths exposed.
 */
@Service
public class RawMediaUploadService {

    private static final Logger log = LoggerFactory.getLogger(RawMediaUploadService.class);

    /** Default bucket for uploaded raw media. Configurable via {@code ingest.upload.bucket}. */
    public static final String DEFAULT_UPLOAD_BUCKET = "uploads";

    private final BlobStorage blobStorage;
    private final AssetRepository assetRepository;
    private final RawMediaProductRegistrationFacade productRegistrationFacade;

    public RawMediaUploadService(
            BlobStorage blobStorage,
            AssetRepository assetRepository,
            RawMediaProductRegistrationFacade productRegistrationFacade) {
        this.blobStorage = blobStorage;
        this.assetRepository = assetRepository;
        this.productRegistrationFacade = productRegistrationFacade;
    }

    /**
     * Upload raw media and register as a RAW_MEDIA Product.
     *
     * @param tenantId    tenant from path variable
     * @param projectId   project from path variable
     * @param fileBytes   uploaded file content
     * @param filename    original filename (used for storage key and asset metadata)
     * @param contentType MIME type (declared by client or detected)
     * @param displayName optional human-readable name (unused for now, reserved)
     * @return the registered upload result
     */
    @Transactional
    public RawMediaUploadResult upload(String tenantId, String projectId, byte[] fileBytes,
                          String filename, String contentType, String displayName) {
        // 1. Generate IDs
        String assetId = Ids.newId("asset");
        String safeFilename = sanitizeFilename(filename);

        // 2. Generate storage key and write to blob storage
        String storageKey = StorageKeyPolicy.assetPath(tenantId, null, projectId, assetId, safeFilename);
        String bucket = DEFAULT_UPLOAD_BUCKET;

        PutObjectCommand command = new PutObjectCommand(bucket, storageKey, fileBytes, contentType);
        StorageObjectRef ref = blobStorage.put(command);
        log.info("Upload stored: tenant={} project={} asset={} bucket={} key={}",
                tenantId, projectId, assetId, ref.bucket(), ref.objectKey());

        // 3. Register asset
        Asset asset = assetRepository.register(
                tenantId, projectId, storageKey,
                detectMediaType(contentType, safeFilename),
                safeFilename,
                fileBytes != null ? (long) fileBytes.length : null,
                null, // checksum (future)
                null, // durationMs (future: probe)
                null, // width (future: probe)
                null  // height (future: probe)
        );
        log.info("Asset registered: id={} mediaType={}", asset.id(), asset.mediaType());

        // 4. Build storage reference URI (provider://bucket/key)
        String storageReferenceUri = blobStorage.code() + "://" + ref.bucket() + "/" + ref.objectKey();

        // 5. Register RAW_MEDIA Product through the render API boundary.
        RawMediaProductRegistrationResult registered = productRegistrationFacade.registerRawMedia(
                new RawMediaProductRegistrationCommand(
                        tenantId,
                        projectId,
                        asset.id(),
                        storageReferenceUri,
                        contentType
                )
        );
        log.info("RAW_MEDIA Product registered: id={} assetId={}", registered.productId(), asset.id());

        return new RawMediaUploadResult(registered.productId(), registered.createdAt());
    }

    /**
     * Detect media type from content type or filename extension.
     */
    static String detectMediaType(String contentType, String filename) {
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.startsWith("video/")) return "VIDEO";
            if (ct.startsWith("audio/")) return "AUDIO";
            if (ct.startsWith("image/")) return "IMAGE";
            if (ct.contains("subtitle") || ct.contains("subrip") || ct.contains("srt") || ct.contains("vtt")) return "SUBTITLE";
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".mkv") || lower.endsWith(".webm")) return "VIDEO";
            if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac") || lower.endsWith(".aac") || lower.endsWith(".ogg")) return "AUDIO";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp")) return "IMAGE";
            if (lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ass")) return "SUBTITLE";
        }
        return "UNKNOWN";
    }

    /**
     * Sanitize filename for storage: remove path separators, null bytes, limit length.
     */
    static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "upload";
        // Strip path separators (defense in depth — StorageKeyPolicy also validates)
        String safe = filename.replaceAll("[/\\\\]", "_");
        // Remove null bytes
        safe = safe.replace("\0", "");
        // Limit length
        if (safe.length() > 255) {
            safe = safe.substring(0, 255);
        }
        return safe.isBlank() ? "upload" : safe;
    }
}
