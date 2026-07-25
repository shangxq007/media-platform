package com.example.platform.render.app.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT;


/**
 * Detects dangling timeline clip {@code assetId} references and tombstoned assets still referenced by clips.
 */
@Service
public class TimelineAssetIntegrityScanner {

    private final DSLContext dsl;

    public TimelineAssetIntegrityScanner(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ScanResult scanProject(String projectId) {
        List<Finding> findings = new ArrayList<>();
        var rows = dsl.select(TIMELINE_SNAPSHOT.ID, TIMELINE_SNAPSHOT.PAYLOAD_JSON)
                .from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.PROJECT_ID.eq(projectId))
                .orderBy(TIMELINE_SNAPSHOT.CREATED_AT.desc())
                .limit(100)
                .fetch();
        for (var row : rows) {
            String snapshotId = row.get(TIMELINE_SNAPSHOT.ID);
            String payload = row.get(TIMELINE_SNAPSHOT.PAYLOAD_JSON);
            if (payload == null || payload.isBlank()) {
                continue;
            }
            try {
                JsonNode root = InternalTimelineJson.parse(payload);
                if (!InternalTimelineJson.isInternalTimeline(root)) {
                    continue;
                }
                JsonNode registry = root.path("assetRegistry").path("assets");
                scanClips(root, snapshotId, registry, findings);
            } catch (Exception ignored) {
                // skip malformed snapshot
            }
        }
        return new ScanResult(projectId, findings);
    }

    private static void scanClips(JsonNode root, String snapshotId, JsonNode registry, List<Finding> findings) {
        JsonNode tracks = root.path("composition").path("tracks");
        if (!tracks.isArray()) {
            return;
        }
        for (JsonNode track : tracks) {
            String trackId = track.path("id").asText("track");
            JsonNode clips = track.path("clips");
            if (!clips.isArray()) {
                continue;
            }
            for (JsonNode clip : clips) {
                String assetId = clip.path("assetId").asText("");
                if (assetId.isBlank()) {
                    continue;
                }
                String clipId = clip.path("id").asText("clip");
                if (!registry.isObject() || !registry.has(assetId)) {
                    findings.add(new Finding(
                            "AST-001", snapshotId, assetId, trackId + "/" + clipId,
                            "Clip references assetId missing from assetRegistry"));
                    continue;
                }
                String status = registry.get(assetId).path("status").asText("ACTIVE");
                if ("TOMBSTONED".equalsIgnoreCase(status) || "PURGED".equalsIgnoreCase(status)) {
                    findings.add(new Finding(
                            "AST-001", snapshotId, assetId, trackId + "/" + clipId,
                            "Clip references tombstoned asset in assetRegistry"));
                }
                String uri = registry.get(assetId).path("uri").asText("");
                if (uri.isBlank() || uri.startsWith("asset://")) {
                    findings.add(new Finding(
                            "AST-003", snapshotId, assetId, trackId + "/" + clipId,
                            "Asset registry entry has no resolvable storage URI"));
                }
            }
        }
    }

    public record Finding(String ruleId, String snapshotId, String assetId, String clipRef, String message) {}

    public record ScanResult(String projectId, List<Finding> findings) {}
}
