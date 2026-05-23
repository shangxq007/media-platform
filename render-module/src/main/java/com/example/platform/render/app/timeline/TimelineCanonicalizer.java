package com.example.platform.render.app.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

/**
 * Canonicalizes Internal Timeline Schema 1.0 JSON (sort by id, strip volatile fields).
 */
@Service
public class TimelineCanonicalizer {

    public CanonicalizeResult canonicalize(String timelineJson) throws java.io.IOException {
        JsonNode root = InternalTimelineJson.parse(timelineJson);
        if (!InternalTimelineJson.isInternalTimeline(root)) {
            throw new IllegalArgumentException(
                    "Expected Internal Timeline schemaVersion 1.0 with composition; use import_otio for exchange formats");
        }
        ObjectNode objectRoot = (ObjectNode) root.deepCopy();
        InternalTimelineJson.sortKeyedEntityMaps(objectRoot);
        if (!objectRoot.has("schemaVersion")) {
            objectRoot.put("schemaVersion", InternalTimelineJson.SCHEMA_V1);
        }
        JsonNode canonical = InternalTimelineJson.deepCanonicalize(objectRoot);
        return new CanonicalizeResult(
                InternalTimelineJson.write(canonical),
                InternalTimelineJson.schemaVersion(canonical),
                InternalTimelineJson.timelineId(canonical),
                InternalTimelineJson.revision(canonical));
    }

    public record CanonicalizeResult(
            String timelineJson,
            String schemaVersion,
            String timelineId,
            int revision) {}
}
