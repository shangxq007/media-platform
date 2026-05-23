package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.infrastructure.TimelineAssetGcProperties;
import com.example.platform.storage.domain.BlobStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Purges deletable tombstoned entries from Internal Timeline {@code assetRegistry} and optionally deletes blobs.
 */
@Service
public class TimelineAssetGcService {

    private static final Logger log = LoggerFactory.getLogger(TimelineAssetGcService.class);

    private final DSLContext dsl;
    private final TimelineSnapshotService timelineSnapshotService;
    private final TimelineAssetLifecycleService lifecycleService;
    private final TimelineAssetGcProperties properties;
    private final Optional<BlobStorage> blobStorage;

    public TimelineAssetGcService(
            DSLContext dsl,
            TimelineSnapshotService timelineSnapshotService,
            TimelineAssetLifecycleService lifecycleService,
            TimelineAssetGcProperties properties,
            @Autowired(required = false) BlobStorage blobStorage) {
        this.dsl = dsl;
        this.timelineSnapshotService = timelineSnapshotService;
        this.lifecycleService = lifecycleService;
        this.properties = properties;
        this.blobStorage = Optional.ofNullable(blobStorage);
    }

    public GcRunResult runGlobalGc() {
        List<String> projectIds = listDistinctProjectIds();
        int limit = Math.max(1, properties.getMaxProjectsPerRun());
        int scanned = 0;
        int purged = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (String projectId : projectIds.stream().limit(limit).toList()) {
            scanned++;
            try {
                GcProjectResult r = runProjectGc(projectId, null);
                purged += r.purged();
                skipped += r.skipped();
                errors.addAll(r.errors());
            } catch (Exception e) {
                skipped++;
                errors.add(projectId + ": " + e.getMessage());
            }
        }
        return new GcRunResult(scanned, purged, skipped, errors);
    }

    @Transactional
    public GcProjectResult runProjectGc(String projectId, String tenantId) {
        Optional<TimelineSnapshotService.SnapshotInfo> latest = timelineSnapshotService.findLatestByProject(projectId);
        if (latest.isEmpty()) {
            return new GcProjectResult(projectId, 0, 0, 0, List.of());
        }
        Instant cutoff = Instant.now().minus(Math.max(1, properties.getRetentionDays()), ChronoUnit.DAYS);
        int candidates = 0;
        int purged = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        try {
            JsonNode root = InternalTimelineJson.parse(latest.get().payloadJson());
            if (!InternalTimelineJson.isInternalTimeline(root)) {
                return new GcProjectResult(projectId, 0, 0, 0, List.of());
            }
            ObjectNode doc = (ObjectNode) root.deepCopy();
            ObjectNode assets = doc.with("assetRegistry").with("assets");
            List<String> assetIds = new ArrayList<>();
            assets.fieldNames().forEachRemaining(assetIds::add);
            boolean changed = false;
            for (String assetId : assetIds) {
                JsonNode entry = assets.get(assetId);
                if (entry == null || !entry.isObject()) {
                    continue;
                }
                String status = entry.path("status").asText("ACTIVE");
                if (!"TOMBSTONED".equalsIgnoreCase(status)) {
                    continue;
                }
                candidates++;
                Instant tombstonedAt = parseInstant(entry.path("tombstonedAt").asText(null));
                if (tombstonedAt != null && tombstonedAt.isAfter(cutoff)) {
                    skipped++;
                    continue;
                }
                var check = lifecycleService.deleteCheck(projectId, assetId);
                if (!check.deletable()) {
                    skipped++;
                    continue;
                }
                String uri = entry.path("uri").asText("");
                if (properties.isDeleteBlobOnPurge()) {
                    deleteBlob(uri);
                }
                ObjectNode updated = (ObjectNode) entry;
                updated.put("status", "PURGED");
                updated.put("purgedAt", Instant.now().toString());
                purged++;
                changed = true;
                log.info("Purged timeline asset project={} assetId={}", projectId, assetId);
            }
            if (!changed) {
                return new GcProjectResult(projectId, candidates, purged, skipped, errors);
            }
            int rev = doc.path("revision").asInt(0);
            doc.put("revision", rev + 1);
            String patched = InternalTimelineJson.write(doc);
            String effectiveTenant = tenantId != null ? tenantId : latest.get().tenantId();
            timelineSnapshotService.save(projectId, effectiveTenant, patched, latest.get().schemaVersion());
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
        return new GcProjectResult(projectId, candidates, purged, skipped, errors);
    }

    /**
     * Tombstones registry entries that share the same storage URI (artifact catalog sync).
     */
    @Transactional
    public int tombstoneRegistryByStorageUri(String projectId, String storageUri, String tenantId) {
        if (storageUri == null || storageUri.isBlank()) {
            return 0;
        }
        Optional<TimelineSnapshotService.SnapshotInfo> latest =
                timelineSnapshotService.findLatestByProject(projectId);
        if (latest.isEmpty()) {
            return 0;
        }
        int count = 0;
        try {
            JsonNode root = InternalTimelineJson.parse(latest.get().payloadJson());
            if (!InternalTimelineJson.isInternalTimeline(root)) {
                return 0;
            }
            ObjectNode doc = (ObjectNode) root.deepCopy();
            ObjectNode assets = doc.with("assetRegistry").with("assets");
            Iterator<String> names = assets.fieldNames();
            boolean changed = false;
            while (names.hasNext()) {
                String assetId = names.next();
                JsonNode entry = assets.get(assetId);
                if (entry == null || !storageUri.equals(entry.path("uri").asText(""))) {
                    continue;
                }
                String status = entry.path("status").asText("ACTIVE");
                if (!"ACTIVE".equalsIgnoreCase(status)) {
                    continue;
                }
                var check = lifecycleService.deleteCheck(projectId, assetId);
                if (!check.deletable()) {
                    continue;
                }
                ObjectNode updated = (ObjectNode) entry;
                updated.put("status", "TOMBSTONED");
                updated.put("tombstonedAt", Instant.now().toString());
                count++;
                changed = true;
            }
            if (!changed) {
                return 0;
            }
            int rev = doc.path("revision").asInt(0);
            doc.put("revision", rev + 1);
            String patched = InternalTimelineJson.write(doc);
            String effectiveTenant = tenantId != null ? tenantId : latest.get().tenantId();
            timelineSnapshotService.save(projectId, effectiveTenant, patched, latest.get().schemaVersion());
        } catch (Exception e) {
            log.warn("Failed to sync timeline tombstone for uri={}: {}", storageUri, e.getMessage());
        }
        return count;
    }

    private List<String> listDistinctProjectIds() {
        return dsl.selectDistinct(field("project_id", String.class))
                .from(table("timeline_snapshot"))
                .fetch(field("project_id", String.class));
    }

    private void deleteBlob(String storageUri) {
        if (storageUri == null || storageUri.isBlank() || blobStorage.isEmpty()) {
            return;
        }
        blobStorage.get().deleteStorageUri(storageUri);
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public record GcRunResult(int projectsScanned, int assetsPurged, int skipped, List<String> errors) {}

    public record GcProjectResult(String projectId, int candidates, int purged, int skipped, List<String> errors) {}
}
