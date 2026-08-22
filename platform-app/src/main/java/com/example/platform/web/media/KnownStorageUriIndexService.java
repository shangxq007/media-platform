package com.example.platform.web.media;

import com.example.platform.artifact.app.ArtifactCatalogRepository;
import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.delivery.app.DeliveryDestinationUriIndexService;
import com.example.platform.timeline.app.InternalTimelineJson;
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
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryJob.DELIVERY_JOB;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJob.RENDER_JOB;


/**
 * Builds the set of storage URIs considered "in use" across catalog, render, delivery, and timelines.
 */
@Service
public class KnownStorageUriIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnownStorageUriIndexService.class);

    private final Optional<DSLContext> dsl;
    private final Optional<ArtifactCatalogRepository> artifactRepository;
    private final Optional<DeliveryDestinationUriIndexService> destinationUriIndex;
    private final com.example.platform.timeline.app.SystemMaintenanceReader systemMaintenanceReader;
    private final BlobStorage blobStorage;

    public KnownStorageUriIndexService(
            @Autowired(required = false) DSLContext dsl,
            @Autowired(required = false) ArtifactCatalogRepository artifactRepository,
            @Autowired(required = false) DeliveryDestinationUriIndexService destinationUriIndex,
            com.example.platform.timeline.app.SystemMaintenanceReader systemMaintenanceReader,
            BlobStorage blobStorage) {
        this.dsl = Optional.ofNullable(dsl);
        this.artifactRepository = Optional.ofNullable(artifactRepository);
        this.destinationUriIndex = Optional.ofNullable(destinationUriIndex);
        this.systemMaintenanceReader = systemMaintenanceReader;
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
        for (ArtifactCatalogEntry artifact : artifactRepository.get().findAll()) {
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
            dsl.get().select(RENDER_JOB.ARTIFACT_URI)
                    .from(RENDER_JOB)
                    .where(RENDER_JOB.ARTIFACT_URI.isNotNull())
                    .fetch(RENDER_JOB.ARTIFACT_URI)
                    .forEach(uri -> addUri(index, uri));
            dsl.get().select(DELIVERY_JOB.SOURCE_URI, DELIVERY_JOB.REMOTE_URI)
                    .from(DELIVERY_JOB)
                    .fetch(record -> {
                        addUri(index, record.get(DELIVERY_JOB.SOURCE_URI));
                        addUri(index, record.get(DELIVERY_JOB.REMOTE_URI));
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
        for (String projectId : systemMaintenanceReader.listProjectIdsWithSnapshots()) {
            systemMaintenanceReader.findLatestSnapshot(projectId).ifPresent(snapshot -> {
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
