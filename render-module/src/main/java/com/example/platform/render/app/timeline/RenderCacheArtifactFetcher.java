package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import com.example.platform.storage.domain.BlobStorage;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fetches remote cache artifact bytes via {@link BlobStorage} when local files are absent.
 */
@Service
public class RenderCacheArtifactFetcher {

    private final BlobStorage blobStorage;
    private final TimelineScriptParser timelineScriptParser;
    private final ErrorCodeRegistry errorCodeRegistry;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public RenderCacheArtifactFetcher(BlobStorage blobStorage,
                                        TimelineScriptParser timelineScriptParser,
                                        ErrorCodeRegistry errorCodeRegistry) {
        this.blobStorage = blobStorage;
        this.timelineScriptParser = timelineScriptParser;
        this.errorCodeRegistry = errorCodeRegistry;
    }

    /**
     * Best-effort fetch; returns empty for missing local paths. Remote blob URIs throw
     * {@link com.example.platform.shared.web.PlatformException} with {@code STORAGE-404-001} when absent.
     */
    public Optional<byte[]> fetchBytes(String storageUri) {
        if (storageUri == null || storageUri.isBlank()) {
            return Optional.empty();
        }
        var remoteRef = BlobStorage.parseUri(storageUri);
        if (remoteRef.isPresent()) {
            var ref = remoteRef.get();
            Optional<byte[]> bytes = blobStorage.get(ref.bucket(), ref.objectKey());
            if (bytes.isEmpty()) {
                throw MediaAssetErrors.storageNotFound(errorCodeRegistry, storageUri);
            }
            return bytes;
        }
        if (timelineScriptParser.mediaFileExists(storageUri, storageRoot)) {
            try {
                String local = timelineScriptParser.resolveLocalPath(storageUri, storageRoot);
                return Optional.of(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(local)));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Strict fetch: propagates {@code STORAGE-404-001} for missing remote objects. */
    public byte[] requireBytes(String storageUri) {
        return fetchBytes(storageUri).orElseThrow(() ->
                MediaAssetErrors.storageNotFound(errorCodeRegistry, storageUri));
    }
}
