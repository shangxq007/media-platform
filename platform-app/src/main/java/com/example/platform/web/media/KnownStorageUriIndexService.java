package com.example.platform.web.media;

import com.example.platform.artifact.app.ArtifactCatalogRepository;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.delivery.app.DeliveryDestinationUriIndexService;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.timeline.InternalTimelineJson;
import com.example.platform.storage.domain.BlobStorage;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Builds the set of storage URIs considered "in use" across catalog, render, delivery, and timelines.
 */
@Service
public class KnownStorageUriIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnownStorageUriIndexService.class);

    private final Optional<DSLContext> dsl;
    private final Optional<ArtifactCatalogRepository> artifactRepository;
    private final Optional<DeliveryDestinationUriIndexService> destinationUriIndex;
    private final TimelineSnapshotService timelineSnapshotService;
    private final BlobStorage blobStorage;

    public KnownStorageUriIndexService(
            @Autowired(required = false) DSLContext dsl,
            @Autowired(required = false) ArtifactCatalogRepository artifactRepository,
            @Autowired(required = false) DeliveryDestinationUriIndexService destinationUriIndex,
            TimelineSnapshotService timelineSnapshotService,
            BlobStorage blobStorage) {
        this.dsl = Optional.ofNullable(dsl);
        this.artifactRepository = Optional.ofNullable(artifactRepository);
        this.destinationUriIndex = Optional.ofNullable(destinationUriIndex);
        this.timelineSnapshotService = timelineSnapshotService;
        this.blobStorage = blobStorage;
    }

    public Set<String> buildKnownUriIndex() {
        Set<String> index = new HashSet<>();
        indexFromArtifacts(index);
        indexFromDatabase(index);
        indexFromDeliveryDestinations(index);
        indexFromTimelines(index);
        return index;
    }

    private void indexFromArtifacts(Set<String> index) {
        if (artifactRepository.isEmpty()) {
            return;
        }
        for (Artifact artifact : artifactRepository.get().findAll()) {
            if (artifact.status() == ArtifactStatus.PURGED) {
                continue;
            }
            addUri(index, artifact.storageUri());
        }
    }

    private void indexFromDatabase(Set<String> index) {
        if (dsl.isEmpty()) {
            return;
        }
        try {
            dsl.get().select(field("artifact_uri", String.class))
                    .from(table("render_job"))
                    .where(field("artifact_uri").isNotNull())
                    .fetch(field("artifact_uri", String.class))
                    .forEach(uri -> addUri(index, uri));
            dsl.get().select(field("source_uri", String.class), field("remote_uri", String.class))
                    .from(table("delivery_job"))
                    .fetch(record -> {
                        addUri(index, record.get(field("source_uri", String.class)));
                        addUri(index, record.get(field("remote_uri", String.class)));
                        return null;
                    });
        } catch (Exception e) {
            log.debug("DB URI index partial skip: {}", e.getMessage());
        }
    }

    private void indexFromDeliveryDestinations(Set<String> index) {
        destinationUriIndex.ifPresent(service -> {
            for (String prefix : service.collectDestinationUriPrefixes()) {
                if (prefix != null && !prefix.isBlank()) {
                    index.add(normalize(prefix));
                }
            }
        });
    }

    private void indexFromTimelines(Set<String> index) {
        for (String projectId : timelineSnapshotService.listDistinctProjectIds()) {
            timelineSnapshotService.findLatestByProject(projectId).ifPresent(snapshot -> {
                try {
                    JsonNode root = InternalTimelineJson.parse(snapshot.payloadJson());
                    JsonNode registry = root.path("assetRegistry").path("assets");
                    if (!registry.isObject()) {
                        return;
                    }
                    registry.fields().forEachRemaining(entry -> {
                        String status = entry.getValue().path("status").asText("ACTIVE");
                        if ("PURGED".equalsIgnoreCase(status)) {
                            return;
                        }
                        addUri(index, entry.getValue().path("uri").asText(null));
                    });
                } catch (Exception ignored) {
                    // skip malformed snapshot
                }
            });
        }
    }

    private void addUri(Set<String> index, String uri) {
        if (uri == null || uri.isBlank() || uri.startsWith("asset://")) {
            return;
        }
        index.add(normalize(uri));
        BlobStorage.parseUri(uri).ifPresent(ref ->
                index.add(normalize(ref.provider() + "://" + ref.bucket() + "/" + ref.objectKey())));
    }

    static String normalize(String uri) {
        return uri.trim();
    }

    public String providerCode() {
        return blobStorage.code();
    }
}
