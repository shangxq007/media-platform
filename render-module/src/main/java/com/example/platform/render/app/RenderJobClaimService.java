package com.example.platform.render.app;

import com.example.platform.render.infrastructure.RenderJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for atomic start claim that commits in a separate transaction.
 * Uses REQUIRES_NEW so the claim survives even if the outer execution rolls back.
 */
@Service
public class RenderJobClaimService {

    private static final Logger log = LoggerFactory.getLogger(RenderJobClaimService.class);

    private final RenderJobRepository renderJobRepository;

    public RenderJobClaimService(RenderJobRepository renderJobRepository) {
        this.renderJobRepository = renderJobRepository;
    }

    /**
     * Atomic CAS claim: QUEUED → SELECTING_PROVIDER.
     * Commits in a separate transaction (REQUIRES_NEW).
     * Returns true if this request won the claim.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimForSelection(String jobId) {
        int claimed = renderJobRepository.claimForSelection(jobId);
        if (claimed > 0) {
            log.info("Claimed render job {} for selection", jobId);
            return true;
        } else {
            log.info("Render job {} already claimed", jobId);
            return false;
        }
    }
}
