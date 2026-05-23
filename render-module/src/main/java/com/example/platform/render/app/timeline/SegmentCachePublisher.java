package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineSegment;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Publishes segment artifact URIs keyed by {@link TimelineSegment#cacheKey()} for remote/incremental reuse.
 */
@Service
public class SegmentCachePublisher {

    private final RenderCacheUriResolver cacheUriResolver;
    private final RenderCacheProperties cacheProperties;

    public SegmentCachePublisher(RenderCacheUriResolver cacheUriResolver,
                                 RenderCacheProperties cacheProperties) {
        this.cacheUriResolver = cacheUriResolver;
        this.cacheProperties = cacheProperties;
    }

    public Map<String, Map<String, String>> publish(String tenantId,
                                                  List<TimelineSegment> segments,
                                                  Map<String, String> segmentArtifacts) {
        return publish(tenantId, segments, segmentArtifacts, null);
    }

    public Map<String, Map<String, String>> publish(String tenantId,
                                                  List<TimelineSegment> segments,
                                                  Map<String, String> segmentArtifacts,
                                                  SegmentArtifactUploadService uploadService) {
        Map<String, Map<String, String>> index = new LinkedHashMap<>();
        if (segments == null || segmentArtifacts == null) {
            return index;
        }
        for (TimelineSegment segment : segments) {
            String uri = segmentArtifacts.get(segment.id());
            if (uri == null || uri.isBlank()) {
                continue;
            }
            String remoteUri = cacheUriResolver.resolveRemote(segment.cacheKey(), tenantId);
            if (remoteUri == null || remoteUri.isBlank()) {
                remoteUri = cacheUriResolver.resolve(uri, segment.cacheKey(), tenantId);
            }
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("segmentId", segment.id());
            entry.put("uri", uri);
            entry.put("remoteUri", remoteUri != null && !remoteUri.isBlank() ? remoteUri : uri);
            entry.put("cacheKey", segment.cacheKey());
            if (cacheProperties.isContentHashEnabled() && uploadService != null) {
                uploadService.computeContentHash(uri)
                        .ifPresent(hash -> entry.put("contentHash", hash));
            }
            index.put(segment.cacheKey(), entry);
        }
        return index;
    }

    public static void mergeIntoReuseIndex(Map<String, String> taskIdToUri,
                                           Map<String, Map<String, String>> segmentCacheIndex) {
        if (segmentCacheIndex == null) {
            return;
        }
        for (Map<String, String> entry : segmentCacheIndex.values()) {
            String segmentId = entry.get("segmentId");
            String uri = entry.getOrDefault("remoteUri", entry.get("uri"));
            if (segmentId != null && uri != null && !uri.isBlank()) {
                taskIdToUri.putIfAbsent(segmentId, uri);
            }
        }
    }
}
