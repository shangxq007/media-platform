package com.example.platform.ingest.app;

import com.example.platform.render.api.rawmedia.RawMediaProductRegistrationCommand;
import com.example.platform.render.api.rawmedia.RawMediaProductRegistrationFacade;
import com.example.platform.render.api.rawmedia.RawMediaProductRegistrationResult;
import com.example.platform.media.api.Asset;
import com.example.platform.media.api.MediaAssets;
import com.example.platform.storage.contract.StorageKeyPolicy;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates raw media upload: staged file → BlobStorage → Asset → RAW_MEDIA Product.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Generate storage key via {@link StorageKeyPolicy}</li>
 *   <li>Stream the staged file to {@link BlobStorage} (file-based, never whole-file in memory)</li>
 *   <li>Register {@link Asset} in asset table</li>
 *   <li>Register RAW_MEDIA product through the render API boundary</li>
 * </ol>
 *
 * <p>The blob write is an external side effect and therefore happens OUTSIDE the database
 * transaction; only the two registrations share one transaction boundary. The uploaded bytes
 * are staged to a temp file by the controller, so the service never holds the whole upload
 * in memory.
 *
 * <p>Safety: no signed URLs, no storage internals, no local paths exposed.
 */
@Service
@Profile("legacy-media-disabled")
public class RawMediaUploadService {

    private static final Logger log = LoggerFactory.getLogger(RawMediaUploadService.class);

    /** Default bucket for uploaded raw media. Configurable via {@code ingest.upload.bucket}. */
    public static final String DEFAULT_UPLOAD_BUCKET = "uploads";

    private final BlobStorage blobStorage;
    private final MediaAssets assetRepository;
    private final RawMediaProductRegistrationFacade productRegistrationFacade;
    private final TransactionTemplate registrationTransaction;

    public RawMediaUploadService(
            BlobStorage blobStorage,
            MediaAssets assetRepository,
            RawMediaProductRegistrationFacade productRegistrationFacade,
            PlatformTransactionManager transactionManager) {
        this.blobStorage = blobStorage;
        this.assetRepository = assetRepository;
        this.productRegistrationFacade = productRegistrationFacade;
        this.registrationTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Upload raw media and register as a RAW_MEDIA Product.
     *
     * @param tenantId       tenant from path variable
     * @param projectId      project from path variable
     * @param contentPath    staged uploaded file content (streamed, not read whole)
     * @param filename       original filename (used for storage key and asset metadata)
     * @param contentType    MIME type (declared by client or detected)
     * @param displayName    optional human-readable name (unused for now, reserved)
     * @return the registered upload result
     */
    public RawMediaUploadResult upload(String tenantId, String projectId, Path contentPath,
                          String filename, String contentType, String displayName) {
        assetRepository.requireRegistrationScope(tenantId, projectId);
        // 1. Generate IDs
        String assetId = ("asset_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        String safeFilename = sanitizeFilename(filename);

        // 2. Generate storage key and stream the staged file to blob storage
        String storageKey = StorageKeyPolicy.assetPath(tenantId, null, projectId, assetId, safeFilename);
        String bucket = DEFAULT_UPLOAD_BUCKET;
        long contentLength;
        try {
            contentLength = Files.size(contentPath);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("staged upload content is not readable", e);
        }

        PutObjectCommand command = PutObjectCommand.fromPath(bucket, storageKey, contentPath, contentType);
        StorageObjectRef ref = blobStorage.put(command);
        log.info("Upload stored: tenant={} project={} asset={} bucket={} key={} bytes={}",
                tenantId, projectId, assetId, ref.bucket(), ref.objectKey(), contentLength);

        // 3. Register asset (MCMV2-C: structural truth captured via probe normalization)
        // 4. Build storage reference URI (provider://bucket/key)
        String storageReferenceUri = blobStorage.code() + "://" + ref.bucket() + "/" + ref.objectKey();

        // 5. Register RAW_MEDIA Product through the render API boundary, in one DB transaction.
        return registrationTransaction.execute(status -> {
            Asset asset = assetRepository.register(
                    tenantId, projectId, storageKey,
                    detectMediaType(contentType, safeFilename),
                    safeFilename,
                    contentLength,
                    null // checksum (future)
            );
            log.info("Asset registered: id={} mediaType={}", asset.id(), asset.mediaType());

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
        });
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
