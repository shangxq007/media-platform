package com.example.platform.render.app;

import com.example.platform.render.infrastructure.RenderJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for durable failure transitions that must survive outer transaction rollback.
 * Uses REQUIRES_NEW to commit in an independent transaction.
 */
@Service
public class RenderJobFailureService {

    private static final Logger log = LoggerFactory.getLogger(RenderJobFailureService.class);

    private final RenderJobRepository renderJobRepository;

    public RenderJobFailureService(RenderJobRepository renderJobRepository) {
        this.renderJobRepository = renderJobRepository;
    }

    /**
     * Record a durable failure: EXECUTING → FAILED in a separate committed transaction.
     * Survives even if the calling transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDurableFailure(String jobId, String reason) {
        int updated = renderJobRepository.markActiveJobFailed(jobId, reason);
        if (updated > 0) {
            renderJobRepository.updateErrorMessage(jobId, reason);
            log.info("Durable failure recorded for job {}: {}", jobId, reason);
        } else {
            log.warn("Could not record durable failure for job {} (not in EXECUTING state)", jobId);
        }
    }
}
