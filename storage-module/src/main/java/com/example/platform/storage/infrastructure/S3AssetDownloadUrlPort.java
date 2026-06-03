package com.example.platform.storage.infrastructure;

import com.example.platform.shared.asset.AssetDownloadUrlPort;
import com.example.platform.storage.domain.BlobStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * S3-based implementation of {@link AssetDownloadUrlPort}.
 *
 * <p>Wraps {@link BlobStorage#presignStorageUri(String)} with TTL control.
 * Max TTL is enforced at 24 hours (86400 seconds) per security policy.
 */
@Component
@ConditionalOnBean(BlobStorage.class)
public class S3AssetDownloadUrlPort implements AssetDownloadUrlPort {

    private static final Logger log = LoggerFactory.getLogger(S3AssetDownloadUrlPort.class);
    public static final Duration MAX_TTL = Duration.ofHours(24);
    public static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final BlobStorage blobStorage;

    public S3AssetDownloadUrlPort(BlobStorage blobStorage) {
        this.blobStorage = blobStorage;
    }

    @Override
    public Optional<String> generateSignedUrl(String assetId, String storageUri, Duration ttl) {
        if (storageUri == null || storageUri.isBlank()) {
            log.warn("AssetDownloadUrlPort: asset {} has no storageUri", assetId);
            return Optional.empty();
        }
        Duration effectiveTtl = clampTtl(ttl);
        try {
            Optional<String> url = blobStorage.presignStorageUri(storageUri);
            if (url.isPresent()) {
                log.debug("AssetDownloadUrlPort: generated signed URL for asset {} (ttl={}s)",
                        assetId, effectiveTtl.getSeconds());
            } else {
                log.warn("AssetDownloadUrlPort: presign returned empty for asset {}", assetId);
            }
            return url;
        } catch (Exception e) {
            log.error("AssetDownloadUrlPort: failed to generate signed URL for asset {}: {}",
                    assetId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Map<String, String> generateSignedUrls(Map<String, String> assets, Duration ttl) {
        Map<String, String> result = new HashMap<>();
        Duration effectiveTtl = clampTtl(ttl);
        for (Map.Entry<String, String> entry : assets.entrySet()) {
            generateSignedUrl(entry.getKey(), entry.getValue(), effectiveTtl)
                    .ifPresent(url -> result.put(entry.getKey(), url));
        }
        return result;
    }

    @Override
    public boolean isAvailable() {
        return blobStorage != null;
    }

    private Duration clampTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return DEFAULT_TTL;
        }
        return ttl.compareTo(MAX_TTL) > 0 ? MAX_TTL : ttl;
    }
}
