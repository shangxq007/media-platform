package com.example.platform.render.infrastructure.billing;

/**
 * Status of a billing record.
 */
public enum BillingRecordStatus {
    /**
     * Cost estimated before execution.
     */
    ESTIMATED,

    /**
     * Quota reserved for execution.
     */
    RESERVED,

    /**
     * Execution in progress.
     */
    IN_PROGRESS,

    /**
     * Cost finalized after execution.
     */
    FINALIZED,

    /**
     * Execution failed.
     */
    FAILED,

    /**
     * Billing reversed (refund).
     */
    REVERSED
}
