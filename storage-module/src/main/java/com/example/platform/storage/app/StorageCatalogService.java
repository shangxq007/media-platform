package com.example.platform.storage.app;

import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StorageCatalogPort;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.StorageObjectRef;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class StorageCatalogService implements StorageCatalogPort {

    private final List<BlobStorage> providers;
    private final ArtifactRepository artifactRepository;

    public StorageCatalogService(List<BlobStorage> providers, ArtifactRepository artifactRepository) {
        this.providers = providers;
        this.artifactRepository = artifactRepository;
    }

    public List<String> providerCodes() {
        return providers.stream().map(BlobStorage::code).toList();
    }

    @Override
    public StorageCatalogPort.ArtifactRef registerArtifact(String renderJobId, String projectId, StorageObjectRef providerRef) {
        String artifactId = Ids.newId("art");
        StorageCatalogPort.ArtifactRef info = new StorageCatalogPort.ArtifactRef(
                artifactId, renderJobId, projectId,
                providerRef.bucket() + "/" + providerRef.objectKey(),
                "mp4", "1920x1080", 30L, Instant.now());
        artifactRepository.save(new ArtifactRepository.ArtifactMetadata(
                info.artifactId(), info.renderJobId(), info.projectId(),
                info.storageUri(), info.format(), info.resolution(),
                info.duration(), info.createdAt()));
        return info;
    }

    @Override
    public List<StorageCatalogPort.ArtifactRef> findArtifactsByJob(String renderJobId) {
        return artifactRepository.findByRenderJobId(renderJobId).stream()
                .map(a -> new StorageCatalogPort.ArtifactRef(
                        a.id(), a.renderJobId(), a.projectId(),
                        a.storageUri(), a.format(), a.resolution(),
                        a.duration(), a.createdAt()))
                .toList();
    }

    public List<StorageCatalogPort.ArtifactRef> findArtifactsByProject(String projectId) {
        return artifactRepository.findByProjectId(projectId).stream()
                .map(a -> new StorageCatalogPort.ArtifactRef(
                        a.id(), a.renderJobId(), a.projectId(),
                        a.storageUri(), a.format(), a.resolution(),
                        a.duration(), a.createdAt()))
                .toList();
    }

    @Override
    public Optional<StorageCatalogPort.ArtifactRef> findArtifact(String artifactId) {
        return artifactRepository.findById(artifactId)
                .map(a -> new StorageCatalogPort.ArtifactRef(
                        a.id(), a.renderJobId(), a.projectId(),
                        a.storageUri(), a.format(), a.resolution(),
                        a.duration(), a.createdAt()));
    }
}
