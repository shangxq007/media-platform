package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
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
public final class DirectStorageArtifactMaterializer implements ArtifactMaterializerPort {

    private final ArtifactQueryService artifacts;
    private final Map<StorageProviderId, StorageProvider> providers;
    private final WorkerLocalMaterializationCache localCache;

    public DirectStorageArtifactMaterializer(
            ArtifactQueryService artifacts,
            Map<StorageProviderId, StorageProvider> providers,
            WorkerLocalMaterializationCache localCache) {
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        Objects.requireNonNull(providers, "providers");
        this.providers = Map.copyOf(providers);
        this.localCache = Objects.requireNonNull(localCache, "localCache");
    }

    @Override
    public ArtifactMaterializationResult materialize(String tenantId, ArtifactPin artifactPin)
            throws IOException {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(artifactPin, "artifactPin");
        Artifact artifact = artifacts.getArtifact(tenantId, artifactPin.artifactId())
                .orElseThrow(() -> new ArtifactMaterializationException(
                        "Artifact does not exist or is unauthorized for tenant"));
        if (artifact.state() != ArtifactState.AVAILABLE
                || !artifact.contentDigest().matches(artifactPin.contentDigest())) {
            throw new ArtifactMaterializationException(
                    "Artifact is unavailable or its ContentDigest does not match the pin");
        }
        ArrayList<ArtifactReplicaBinding> replicas = new ArrayList<>(
                artifacts.listReplicas(tenantId, artifactPin.artifactId()));
        replicas.sort(Comparator
                .comparing((ArtifactReplicaBinding value) -> value.providerId().value())
                .thenComparing(value -> value.storageReplicaId().value()));
        return localCache.getOrMaterialize(
                artifactPin,
                () -> openFirstReadableReplica(replicas));
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
