package com.example.platform.timeline.app;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C10/C11): extracts exact Artifact pins
 * (ArtifactId + ContentDigest) from Internal Timeline 1.0 JSON.
 *
 * <p>A pin lives on a clip's {@code sourceBinding} as
 * {@code artifactId} + {@code contentDigest.value}. Extraction is tolerant:
 * clips without a sourceBinding contribute no pins. Distinct pins are returned
 * (one protection row per revision+artifact is sufficient).</p>
 */
public final class TimelineArtifactPinExtractor {

    private TimelineArtifactPinExtractor() {}

    public record ArtifactPin(ArtifactId artifactId, ContentDigest contentDigest) {}

    /** Extract distinct Artifact pins from internal-1.0 JSON (clips in composition.tracks). */
    public static List<ArtifactPin> extract(String internalTimelineJson) {
        Map<String, ArtifactPin> distinct = new LinkedHashMap<>();
        try {
            JsonNode root = com.example.platform.timeline.app.InternalTimelineJson.parse(internalTimelineJson);
            JsonNode tracks = root.path("composition").path("tracks");
            if (!tracks.isArray()) {
                return List.of();
            }
            for (JsonNode track : tracks) {
                JsonNode clips = track.path("clips");
                if (!clips.isArray()) {
                    continue;
                }
                for (JsonNode clip : clips) {
                    JsonNode sb = clip.path("sourceBinding");
                    if (sb.isMissingNode() || sb.isNull() || !sb.isObject()) {
                        continue;
                    }
                    String artifactId = sb.path("artifactId").asText(null);
                    JsonNode digestNode = sb.path("contentDigest");
                    String digestValue = digestNode.path("value").asText(null);
                    if (artifactId == null || digestValue == null) {
                        continue;
                    }
                    try {
                        ArtifactPin pin = new ArtifactPin(
                                new ArtifactId(artifactId),
                                ContentDigest.sha256(digestValue));
                        distinct.putIfAbsent(artifactId, pin);
                    } catch (IllegalArgumentException ignored) {
                        // Malformed pin fields are rejected by canonical validation
                        // (E1b gate); extraction stays tolerant.
                    }
                }
            }
        } catch (Exception e) {
            // Parsing errors are handled by the E1b canonical gate; extractor is tolerant.
            return List.of();
        }
        return new ArrayList<>(distinct.values());
    }
}
