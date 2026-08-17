package com.example.platform.render.app;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.domain.BlobStorage;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1): render is an Artifact CONSUMER.
 *
 * <p>Artifact queries route through the artifact-module contracts (single
 * canonical query authority): job-scoped projection via
 * {@link ArtifactCatalogService}, exact identity/digest/replica via
 * {@link ArtifactQueryService}. Physical byte reads use the storage data-plane
 * ({@link BlobStorage}). Render never queries the canonical artifact table
 * directly and never resolves canonical identity from a storage URI.</p>
 */
@Service
public class RenderArtifactQueryService {

    private final RenderJobRepository renderJobRepository;
    private final ArtifactQueryService artifactQueryService;
    private final ArtifactCatalogService artifactCatalogService;
    private final List<BlobStorage> storageProviders;

    public RenderArtifactQueryService(RenderJobRepository renderJobRepository,
            ArtifactQueryService artifactQueryService,
            ArtifactCatalogService artifactCatalogService,
            List<BlobStorage> storageProviders) {
        this.renderJobRepository = renderJobRepository;
        this.artifactQueryService = artifactQueryService;
        this.artifactCatalogService = artifactCatalogService;
        this.storageProviders = storageProviders;
    }

    /**
     * Get all artifacts associated with a render job (catalog projection over
     * canonical Artifact persistence).
     *
     * @param jobId the render job ID
     * @return list of artifact info responses
     * @throws IllegalArgumentException if job not found / tenant denied
     */
    public List<ArtifactInfoResponse> getArtifactsByJob(String jobId) {
        String jobTenantId = renderJobRepository.requireTenantIdByJobId(jobId);
        assertTenantAccess(jobTenantId);
        return artifactCatalogService.listArtifactsByRenderJob(jobId).stream()
                .map(a -> new ArtifactInfoResponse(
                        a.id(), a.renderJobId(), a.projectId(), a.storageUri(),
                        a.format(), a.resolution(), a.duration() != null ? a.duration() : 0L,
                        a.createdAt()))
                .toList();
    }

    /**
     * Get artifact content bytes.
     *
     * @param artifactId the artifact ID
     * @return content bytes or null if not found
     */
    public byte[] getArtifactContent(String artifactId) {
        ArtifactId id = new ArtifactId(artifactId);
        Optional<Artifact> artifact = artifactQueryService.getArtifact(TenantContext.get(), id);
        if (artifact.isEmpty()) {
            return null;
        }
        // Physical bytes are read from the storage data-plane by replica
        // location; canonical identity is never derived from a URI.
        var replicas = artifactQueryService.listReplicas(TenantContext.get(), id);
        for (var replica : replicas) {
            String objectKey = replica.storageObjectId().value();
            String[] parts = objectKey.split("/", 2);
            if (parts.length < 2) {
                continue;
            }
            String bucket = parts[0];
            String key = parts[1];
            for (BlobStorage provider : storageProviders) {
                var content = provider.get(bucket, key);
                if (content.isPresent()) {
                    return content.get();
                }
            }
        }
        return null;
    }

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant");
        }
    }
}
