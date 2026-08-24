package com.example.platform.render.infrastructure.farm;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled service that expires stale leases and requeues jobs.
 *
 * <p>Runs periodically to find leases that have exceeded their lease_until time
 * and either requeues the job (if attempts remaining) or marks it as FAILED.
 */
public class StaleRenderJobLeaseCompensationService {

    private static final Logger log = LoggerFactory.getLogger(StaleRenderJobLeaseCompensationService.class);

    private final RenderJobLeaseService leaseService;

    public StaleRenderJobLeaseCompensationService(RenderJobLeaseService leaseService) {
        this.leaseService = leaseService;
    }

    /**
     * Expire stale leases. Runs every 60 seconds by default.
     */
    public void compensateStaleLeases() {
        try {
            int expired = leaseService.expireStaleLeases();
            if (expired > 0) {
                log.info("Stale lease compensation: expired {} leases", expired);
            }
        } catch (Exception e) {
            log.error("Stale lease compensation failed", e);
        }
    }
}
