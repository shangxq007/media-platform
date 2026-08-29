package com.example.platform.billing.usage;

/** Transactional audit side effect required when a new BillableUsage row is appended. */
@FunctionalInterface
public interface BillableUsageAuditPort {
    void recordMetered(BillableUsage usage);
}
