package com.example.platform.render.infrastructure.farm;

/**
 * Status of a render job lease.
 */
public enum RenderJobLeaseStatus {
    /** Lease claimed by a worker, job execution starting. */
    CLAIMED,
    /** Job is actively running on the worker. */
    RUNNING,
    /** Lease renewed — extending the lease window. */
    RENEWED,
    /** Lease released — job completed successfully. */
    RELEASED,
    /** Lease expired — worker did not complete or renew in time. */
    EXPIRED,
    /** Lease failed — job execution failed. */
    FAILED
}
