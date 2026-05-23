package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.shared.asset.StorageUriReferenceContributor;
import com.example.platform.shared.asset.StorageUriReferenceHit;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import com.example.platform.shared.web.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Tombstone and reference checks for media assets in Internal Timeline {@code assetRegistry}.
 */
@Service
public class TimelineAssetLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(TimelineAssetLifecycleService.class);

    private final DSLContext dsl;
    private final TimelineSnapshotService timelineSnapshotService;
    private final ErrorCodeRegistry errorCodeRegistry;
    private final List<StorageUriReferenceContributor> referenceContributors;

    public TimelineAssetLifecycleService(DSLContext dsl,
                                         TimelineSnapshotService timelineSnapshotService,
                                         ErrorCodeRegistry errorCodeRegistry,
                                         @Autowired(required = false)
                                         List<StorageUriReferenceContributor> referenceContributors) {
        this.dsl = dsl;
        this.timelineSnapshotService = timelineSnapshotService;
        this.errorCodeRegistry = errorCodeRegistry;
        this.referenceContributors = referenceContributors != null ? referenceContributors : List.of();
    }

    public DeleteCheckResult deleteCheck(String projectId, String assetId) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (SnapshotRow row : listSnapshotsForProject(projectId)) {
            try {
                JsonNode root = InternalTimelineJson.parse(row.payloadJson());
                for (TimelineAssetReferenceScanner.AssetReferenceHit hit
                        : TimelineAssetReferenceScanner.findReferences(root, assetId)) {
                    if ("registry_entry".equals(hit.kind())) {
                        continue;
                    }
                    references.add(Map.of(
                            "snapshotId", row.id(),
                            "path", hit.path(),
                            "entityId", hit.entityId(),
                            "kind", hit.kind()));
                }
            } catch (Exception e) {
                log.debug("Skip snapshot {} during delete-check: {}", row.id(), e.getMessage());
            }
        }
        String storageUri = resolveStorageUri(projectId, assetId);
        if (storageUri != null) {
            references.addAll(findContributorReferences(storageUri, projectId));
        }
        boolean deletable = references.isEmpty();
        return new DeleteCheckResult(assetId, projectId, deletable, references);
    }

    private String resolveStorageUri(String projectId, String assetId) {
        return timelineSnapshotService.findLatestByProject(projectId).flatMap(snapshot -> {
            try {
                JsonNode root = InternalTimelineJson.parse(snapshot.payloadJson());
                JsonNode entry = root.path("assetRegistry").path("assets").path(assetId);
                if (entry.isMissingNode()) {
                    return Optional.empty();
                }
                String uri = entry.path("uri").asText("");
                return uri.isBlank() ? Optional.empty() : Optional.of(uri);
            } catch (Exception e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private List<Map<String, Object>> findContributorReferences(String storageUri, String projectId) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (StorageUriReferenceContributor contributor : referenceContributors) {
            for (StorageUriReferenceHit hit : contributor.findReferences(storageUri, projectId)) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("kind", hit.kind());
                ref.put("contributor", contributor.contributorId());
                ref.put("entityId", hit.entityId());
                ref.put("message", hit.message());
                if (hit.details() != null) {
                    ref.putAll(hit.details());
                }
                refs.add(ref);
            }
        }
        return refs;
    }

    @Transactional
    public TombstoneResult tombstone(String projectId, String snapshotId, String assetId, String tenantId) {
        Optional<TimelineSnapshotService.SnapshotInfo> info = timelineSnapshotService.findById(snapshotId);
        if (info.isEmpty() || !projectId.equals(info.get().projectId())) {
            throw MediaAssetErrors.assetNotFound(errorCodeRegistry, assetId);
        }
        try {
            JsonNode root = InternalTimelineJson.parse(info.get().payloadJson());
            if (!InternalTimelineJson.isInternalTimeline(root)) {
                throw new IllegalArgumentException("Snapshot is not Internal Timeline 1.0");
            }
            ObjectNode doc = (ObjectNode) root;
            ObjectNode assets = doc.with("assetRegistry").with("assets");
            if (!assets.has(assetId)) {
                throw MediaAssetErrors.assetNotFound(errorCodeRegistry, assetId);
            }
            DeleteCheckResult check = deleteCheck(projectId, assetId);
            if (!check.deletable()) {
                throw MediaAssetErrors.assetStillReferenced(errorCodeRegistry, assetId);
            }
            ObjectNode entry = (ObjectNode) assets.get(assetId);
            entry.put("status", "TOMBSTONED");
            entry.put("tombstonedAt", Instant.now().toString());
            int rev = doc.path("revision").asInt(0);
            doc.put("revision", rev + 1);
            String patched = InternalTimelineJson.write(doc);
            String newSnapshotId = timelineSnapshotService.save(
                    projectId, tenantId != null ? tenantId : info.get().tenantId(), patched, info.get().schemaVersion());
            log.info("Tombstoned asset {} in snapshot {} -> {}", assetId, snapshotId, newSnapshotId);
            return new TombstoneResult(assetId, snapshotId, newSnapshotId, "TOMBSTONED");
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to tombstone asset: " + e.getMessage(), e);
        }
    }

    private List<SnapshotRow> listSnapshotsForProject(String projectId) {
        return dsl.select(
                        field("id", String.class),
                        field("payload_json", String.class))
                .from(table("timeline_snapshot"))
                .where(field("project_id").eq(projectId))
                .orderBy(field("created_at").desc())
                .fetch(r -> new SnapshotRow(
                        r.get(field("id", String.class)),
                        r.get(field("payload_json", String.class))));
    }

    public record DeleteCheckResult(
            String assetId,
            String projectId,
            boolean deletable,
            List<Map<String, Object>> references) {}

    public record TombstoneResult(
            String assetId,
            String sourceSnapshotId,
            String newSnapshotId,
            String status) {}

    private record SnapshotRow(String id, String payloadJson) {}
}
