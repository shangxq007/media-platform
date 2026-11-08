package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.api.StoragePlacementQuery;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.replica.ReplicaState;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.storage.contract.read.IntegrityRequirement;
import com.example.platform.storage.contract.read.StorageReadRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Direct materialization through the backend-neutral StorageProvider SPI. */
@org.springframework.modulith.NamedInterface("runtime")
public final class DirectStorageArtifactMaterializer implements ArtifactMaterializerPort {

    private final ArtifactQueryService artifacts;
    private final Map<StorageProviderId, StorageProvider> providers;
    private final WorkerLocalMaterializationCache localCache;
    private final ArtifactCatalogService catalog;
    private final StoragePlacementQuery placements;

    public DirectStorageArtifactMaterializer(
            ArtifactQueryService artifacts,
            Map<StorageProviderId, StorageProvider> providers,
            WorkerLocalMaterializationCache localCache) {
        this(artifacts, providers, localCache, null, null);
    }

    public DirectStorageArtifactMaterializer(ArtifactQueryService artifacts,
            Map<StorageProviderId, StorageProvider> providers, WorkerLocalMaterializationCache localCache,
            ArtifactCatalogService catalog, StoragePlacementQuery placements) {
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        Objects.requireNonNull(providers, "providers");
        this.providers = Map.copyOf(providers);
        this.localCache = Objects.requireNonNull(localCache, "localCache");
        this.catalog = catalog;
        this.placements = placements;
    }

    @Override
    public ArtifactMaterializationResult materialize(String tenantId, ArtifactPin artifactPin)
            throws IOException {
        return materialize(tenantId, null, artifactPin);
    }

    @Override
    public ArtifactMaterializationResult materialize(String tenantId, String projectId, ArtifactPin artifactPin)
            throws IOException {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(artifactPin, "artifactPin");
        Artifact artifact = artifacts.getArtifact(tenantId, artifactPin.artifactId())
                .orElseThrow(() -> new ArtifactMaterializationException(
                        "Artifact does not exist or is unauthorized for tenant"));
        if (!tenantId.equals(artifact.tenantId()) || !artifactPin.artifactId().equals(artifact.artifactId())
                || artifact.state() != ArtifactState.AVAILABLE
                || !artifact.contentDigest().matches(artifactPin.contentDigest())) {
            throw new ArtifactMaterializationException(
                    "Artifact is unavailable or its ContentDigest does not match the pin");
        }
        if (catalog != null && (projectId == null || projectId.isBlank()
                || catalog.findArtifact(tenantId, projectId, artifactPin.artifactId().value())
                        .filter(entry -> entry.isUsable()).isEmpty())) {
            throw new ArtifactMaterializationException("Artifact is unavailable in the execution project");
        }
        ArrayList<ArtifactReplicaBinding> replicas = new ArrayList<>(
                artifacts.listReplicas(tenantId, artifactPin.artifactId()));
        if (replicas.stream().anyMatch(replica -> !artifactPin.artifactId().equals(replica.artifactId()))) {
            throw new ArtifactMaterializationException("Artifact replica identity mismatch");
        }
        replicas.sort(Comparator
                .comparing((ArtifactReplicaBinding value) -> value.providerId().value())
                .thenComparing(value -> value.storageReplicaId().value()));
        if (artifact.artifactKind() == ArtifactKind.SOURCE_MEDIA
                && replicas.stream().noneMatch(replica -> providers.containsKey(replica.providerId()))) {
            // Resolve the exact committed placement even on cache hits: cache possession is not authorization.
            if (placements == null || projectId == null || projectId.isBlank()) {
                throw new ArtifactMaterializationException("source receipt requires execution project scope");
            }
            var owner = new StorageOwnershipScope(tenantId, projectId);
            var matching = replicas.stream().filter(replica -> placements.findPlacement(owner,
                    replica.storageObjectId(), replica.storageReplicaId()).filter(issued ->
                        owner.equals(issued.owner()) && issued.objectId().equals(replica.storageObjectId())
                        && issued.placement().replicaId().equals(replica.storageReplicaId())
                        && issued.receipt().purpose() == com.example.platform.storage.api.StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE
                        && issued.placement().location().providerId().equals(replica.providerId())
                        && tenantId.equals(issued.placement().location().namespace().tenantId())
                        && projectId.equals(issued.placement().location().namespace().projectId())
                        && issued.placement().location().namespace().namespaceClass() == NamespaceClass.SOURCE
                        && issued.placement().state() == ReplicaState.AVAILABLE
                        && issued.placement().committedDigest().matches(artifact.contentDigest())
                        && issued.placement().committedLength() == artifact.byteLength()).isPresent())
                    .findFirst().orElseThrow(() -> new ArtifactMaterializationException("no matching committed project source receipt"));
            var result = localCache.getOrMaterialize(artifactPin, () -> {
                try {
                    return new java.io.ByteArrayInputStream(placements.read(owner,
                            matching.storageObjectId(), matching.storageReplicaId()));
                } catch (RuntimeException failure) {
                    throw new ArtifactMaterializationException("committed project source read failed", failure);
                }
            });
            if (result.materializedArtifact().byteLength() != artifact.byteLength()) {
                throw new ArtifactMaterializationException("Artifact byte length mismatch");
            }
            return result;
        }
        var result = localCache.getOrMaterialize(artifactPin, () -> openFirstReadableReplica(replicas));
        if (result.materializedArtifact().byteLength() != artifact.byteLength()) {
            throw new ArtifactMaterializationException("Artifact byte length mismatch");
        }
        return result;
    }

    private java.io.InputStream openFirstReadableReplica(
            java.util.List<ArtifactReplicaBinding> replicas) throws IOException {
        for (ArtifactReplicaBinding replica : replicas) {
            StorageProvider provider = providers.get(replica.providerId());
            if (provider == null) {
                continue;
            }
            Optional<java.io.InputStream> source = provider.openRead(new StorageReadRequest(
                    replica.storageObjectId(), Optional.empty(), IntegrityRequirement.VERIFY_DIGEST));
            if (source.isPresent()) {
                return source.orElseThrow();
            }
        }
        throw new ArtifactMaterializationException(
                "no configured StorageProvider could read an Artifact replica");
    }
}
