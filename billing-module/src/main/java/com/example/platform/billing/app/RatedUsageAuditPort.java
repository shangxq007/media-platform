package com.example.platform.billing.app;

import com.example.platform.billing.domain.RatedUsageRecord;

@FunctionalInterface
public interface RatedUsageAuditPort {
    void record(RatedUsageRecord record);
}
