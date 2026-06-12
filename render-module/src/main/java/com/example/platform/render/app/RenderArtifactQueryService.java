package com.example.platform.render.app;

import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StorageCatalogPort;
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

    public RenderArtifactQueryService(RenderJobRepository renderJobRepository,
            StorageCatalogPort storageCatalogPort) {
        this.renderJobRepository = renderJobRepository;
        this.storageCatalogPort = storageCatalogPort;
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

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant");
        }
    }
}
