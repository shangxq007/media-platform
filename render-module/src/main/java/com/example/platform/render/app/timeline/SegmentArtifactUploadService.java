package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSegment;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Uploads segment render artifacts to blob storage and binds {@code cacheKey} to durable URIs.
 */
@Service
public class SegmentArtifactUploadService {

    private static final Logger log = LoggerFactory.getLogger(SegmentArtifactUploadService.class);
    private static final String CACHE_BUCKET = "render-cache";

    private final BlobStorage blobStorage;
    private final RenderCacheProperties cacheProperties;
    private final TimelineScriptParser timelineScriptParser;
    private final RenderCacheUriResolver cacheUriResolver;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public SegmentArtifactUploadService(BlobStorage blobStorage,
                                        RenderCacheProperties cacheProperties,
                                        TimelineScriptParser timelineScriptParser,
                                        RenderCacheUriResolver cacheUriResolver) {
        this.blobStorage = blobStorage;
        this.cacheProperties = cacheProperties;
        this.timelineScriptParser = timelineScriptParser;
        this.cacheUriResolver = cacheUriResolver;
    }

    public boolean isUploadEnabled() {
        return cacheProperties.isUploadEnabled();
    }

    public Optional<String> uploadSegmentArtifact(String tenantId,
                                                  String segmentId,
                                                  String cacheKey,
                                                  String localStorageUri) {
        if (!isUploadEnabled() || localStorageUri == null || localStorageUri.isBlank()) {
            return Optional.empty();
        }
        try {
            if (!timelineScriptParser.mediaFileExists(localStorageUri, storageRoot)) {
                log.warn("Segment upload skipped, file missing: segment={} uri={}", segmentId, localStorageUri);
                return Optional.empty();
            }
            Path localPath = Path.of(timelineScriptParser.resolveLocalPath(localStorageUri, storageRoot));
            byte[] bytes = Files.readAllBytes(localPath);
            String objectKey = objectKeyFor(tenantId, cacheKey, segmentId, "mp4");
            StorageObjectRef ref = blobStorage.put(
                    new PutObjectCommand(CACHE_BUCKET, objectKey, bytes, "video/mp4"));
            String uploadedUri = toStorageUri(ref);
            log.info("Uploaded segment artifact segment={} cacheKey={} uri={} bytes={}",
                    segmentId, cacheKey, uploadedUri, bytes.length);
            return Optional.of(uploadedUri);
        } catch (IOException e) {
            log.warn("Segment artifact upload failed segment={}: {}", segmentId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Publishes segment cache index and optionally replaces {@code remoteUri} with uploaded blob URIs.
     */
    public Map<String, Map<String, String>> publishWithUpload(String tenantId,
                                                              List<TimelineSegment> segments,
                                                              Map<String, String> segmentArtifacts,
                                                              SegmentCachePublisher publisher) {
        Map<String, Map<String, String>> index =
                publisher.publish(tenantId, segments, segmentArtifacts, this);
        if (index.isEmpty()) {
            return index;
        }
        if (!isUploadEnabled()) {
            return index;
        }
        Map<String, Map<String, String>> enriched = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : index.entrySet()) {
            Map<String, String> row = new LinkedHashMap<>(entry.getValue());
            String segmentId = row.get("segmentId");
            String cacheKey = row.getOrDefault("cacheKey", entry.getKey());
            String localUri = row.get("uri");
            uploadSegmentArtifact(tenantId, segmentId, cacheKey, localUri)
                    .ifPresent(uploaded -> row.put("remoteUri", uploaded));
            if (!row.containsKey("remoteUri") || row.get("remoteUri") == null) {
                row.put("remoteUri", cacheUriResolver.resolveRemote(cacheKey, tenantId));
            }
            enriched.put(entry.getKey(), Map.copyOf(row));
        }
        return enriched;
    }

    public Optional<String> uploadMezzanineArtifact(String tenantId,
                                                    String cacheKey,
                                                    String taskId,
                                                    String localStorageUri) {
        return uploadSegmentArtifact(tenantId, taskId, cacheKey, localStorageUri);
    }

    public Optional<String> computeContentHash(String localStorageUri) {
        if (!cacheProperties.isContentHashEnabled() || localStorageUri == null || localStorageUri.isBlank()) {
            return Optional.empty();
        }
        try {
            if (!timelineScriptParser.mediaFileExists(localStorageUri, storageRoot)) {
                return Optional.empty();
            }
            Path localPath = Path.of(timelineScriptParser.resolveLocalPath(localStorageUri, storageRoot));
            return Optional.of(RenderCacheContentHasher.hashFile(localPath));
        } catch (IOException e) {
            log.debug("Content hash skipped for uri={}: {}", localStorageUri, e.getMessage());
            return Optional.empty();
        }
    }

    public static String toStorageUri(StorageObjectRef ref) {
        return ref.provider() + "://" + ref.bucket() + "/" + ref.objectKey();
    }

    static String objectKeyFor(String tenantId, String cacheKey, String segmentId) {
        return objectKeyFor(tenantId, cacheKey, segmentId, "mp4");
    }

    static String objectKeyFor(String tenantId, String cacheKey, String segmentId, String extension) {
        String tenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        String key = cacheKey != null && !cacheKey.isBlank()
                ? cacheKey.replace(':', '/')
                : "artifacts/" + segmentId;
        String ext = extension != null && extension.startsWith(".") ? extension : "." + extension;
        return tenant + "/" + key + ext;
    }
}
