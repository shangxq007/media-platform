package com.example.platform.render.infrastructure.farm;

/**
 * Status of a render worker in the farm.
 */
public enum RenderWorkerStatus {
    /** Worker is starting up, not yet ready for jobs. */
    STARTING,
    /** Worker is idle and available for job assignment. */
    IDLE,
    /** Worker is actively executing a job. */
    BUSY,
    /** Worker is draining — finishing current jobs but not accepting new ones. */
    DRAINING,
    /** Worker is offline — no heartbeat received. */
    OFFLINE,
    /** Worker has failed — unrecoverable error. */
    FAILED
}
