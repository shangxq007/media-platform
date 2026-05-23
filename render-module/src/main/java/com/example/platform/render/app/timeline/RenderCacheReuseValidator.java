package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Verifies local reusable artifacts match recorded {@code contentHash} before incremental reuse.
 */
@Service
public class RenderCacheReuseValidator {

    private static final Logger log = LoggerFactory.getLogger(RenderCacheReuseValidator.class);

    private final RenderCacheProperties cacheProperties;
    private final RenderCacheArtifactFetcher artifactFetcher;

    public RenderCacheReuseValidator(RenderCacheProperties cacheProperties,
                                     TimelineScriptParser timelineScriptParser,
                                     RenderCacheArtifactFetcher artifactFetcher) {
        this.cacheProperties = cacheProperties;
        this.artifactFetcher = artifactFetcher;
    }

    public boolean isValidationEnabled() {
        return cacheProperties.isContentHashEnabled();
    }

    public boolean validateLocalArtifact(String storageUri, String expectedContentHash) {
        if (!isValidationEnabled() || expectedContentHash == null || expectedContentHash.isBlank()) {
            return true;
        }
        if (storageUri == null || storageUri.isBlank()) {
            return false;
        }
        try {
            Optional<byte[]> bytes = artifactFetcher.fetchBytes(storageUri);
            if (bytes.isEmpty()) {
                log.debug("Reuse validation skipped, artifact not resolvable uri={}", storageUri);
                return false;
            }
            String actual = RenderCacheContentHasher.hashBytes(bytes.get());
            boolean ok = RenderCacheContentHasher.matches(expectedContentHash, actual);
            if (!ok) {
                log.warn("Cache content hash mismatch uri={} expected={} actual={}",
                        storageUri, expectedContentHash, actual);
            }
            return ok;
        } catch (Exception e) {
            log.warn("Cache hash validation failed for uri={}: {}", storageUri, e.getMessage());
            return false;
        }
    }
}
