package com.example.platform.render.app.preview;

import com.example.platform.render.domain.previewjob.PreviewRenderJob;
import com.example.platform.render.domain.previewjob.PreviewRenderJobId;
import com.example.platform.render.domain.previewjob.PreviewRenderJobRepository;
import com.example.platform.render.domain.previewjob.PreviewRenderJobStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link PreviewRenderJobRepository} for testing.
 *
 * <p>Thread-safe via ConcurrentHashMap. Not suitable for production use.</p>
 */
public class InMemoryPreviewRenderJobRepository implements PreviewRenderJobRepository {

    private final Map<String, PreviewRenderJob> store = new ConcurrentHashMap<>();

    @Override
    public PreviewRenderJob save(PreviewRenderJob job) {
        store.put(job.jobId().value(), job);
        return job;
    }

    @Override
    public Optional<PreviewRenderJob> findById(PreviewRenderJobId jobId) {
        return Optional.ofNullable(store.get(jobId.value()));
    }

    @Override
    public Optional<PreviewRenderJob> findByIdAndTenantAndProject(
            PreviewRenderJobId jobId, String tenantId, String projectId) {
        PreviewRenderJob job = store.get(jobId.value());
        if (job == null) return Optional.empty();
        if (!tenantId.equals(job.tenantId())) return Optional.empty();
        if (!projectId.equals(job.projectId())) return Optional.empty();
        return Optional.of(job);
    }

    @Override
    public List<PreviewRenderJob> listByTenantAndProject(
            String tenantId, String projectId, int limit) {
        return store.values().stream()
                .filter(j -> tenantId.equals(j.tenantId()))
                .filter(j -> projectId.equals(j.projectId()))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .limit(limit)
                .toList();
    }

    @Override
    public void updateStatus(PreviewRenderJobId jobId,
                             PreviewRenderJobStatus newStatus,
                             String outputProductId,
                             String errorMessage) {
        PreviewRenderJob existing = store.get(jobId.value());
        if (existing == null) return;

        PreviewRenderJob updated = new PreviewRenderJob(
                existing.jobId(), existing.tenantId(), existing.projectId(),
                existing.snapshotId(), existing.profile(),
                newStatus, outputProductId, errorMessage,
                existing.createdAt(),
                newStatus.isTerminal() ? java.time.Instant.now() : existing.completedAt());
        store.put(jobId.value(), updated);
    }

    /**
     * Clear all stored jobs (for test isolation).
     */
    public void clear() {
        store.clear();
    }

    /**
     * Return the number of stored jobs.
     */
    public int size() {
        return store.size();
    }
}
