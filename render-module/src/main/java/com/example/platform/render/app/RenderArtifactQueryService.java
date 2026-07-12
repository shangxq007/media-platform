package com.example.platform.render.app;

import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StorageCatalogPort;
import com.example.platform.storage.domain.BlobStorage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only service for querying artifacts associated with render jobs.
 *
 * <p>Extracted from {@link RenderOrchestratorService} to separate the artifact
 * query path from the render execution path. Uses {@link RenderJobRepository}
 * for job existence and tenant validation instead of inline jOOQ.
 */
@Service
public class RenderArtifactQueryService {

    private final RenderJobRepository renderJobRepository;
    private final StorageCatalogPort storageCatalogPort;
    private final List<BlobStorage> storageProviders;

    public RenderArtifactQueryService(RenderJobRepository renderJobRepository,
            StorageCatalogPort storageCatalogPort,
            List<BlobStorage> storageProviders) {
        this.renderJobRepository = renderJobRepository;
        this.storageCatalogPort = storageCatalogPort;
        this.storageProviders = storageProviders;
    }

    /**
     * Get all artifacts associated with a render job.
     *
     * @param jobId the render job ID
     * @return list of artifact info responses
     * @throws IllegalArgumentException if job not found
     * @throws IllegalArgumentException if tenant access denied
     */
    public List<ArtifactInfoResponse> getArtifactsByJob(String jobId) {
        String jobTenantId = renderJobRepository.requireTenantIdByJobId(jobId);
        assertTenantAccess(jobTenantId);

        return storageCatalogPort.findArtifactsByJob(jobId).stream()
                .map(a -> new ArtifactInfoResponse(a.artifactId(), a.renderJobId(), a.projectId(),
                        a.storageUri(), a.format(), a.resolution(), a.duration(), a.createdAt()))
                .toList();
    }

    /**
     * Get artifact content bytes.
     *
     * @param artifactId the artifact ID
     * @return content bytes or null if not found
     */
    public byte[] getArtifactContent(String artifactId) {
        var artifact = storageCatalogPort.findArtifact(artifactId);
        if (artifact.isEmpty()) {
            return null;
        }
        
        String storageUri = artifact.get().storageUri();
        // Parse storageUri: "artifacts/art_xxx/output.mp4"
        String[] parts = storageUri.split("/", 2);
        if (parts.length < 2) {
            return null;
        }
        String bucket = parts[0];
        String objectKey = parts[1];
        
        for (BlobStorage provider : storageProviders) {
            var content = provider.get(bucket, objectKey);
            if (content.isPresent()) {
                return content.get();
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
