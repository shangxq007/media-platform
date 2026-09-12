package com.example.platform.entitlement.api.commercial;

/** Neutral mutation port backed by the sole H5 quota usage authority. */
@FunctionalInterface
public interface QuotaConsumptionPort {
    QuotaDecision consume(QuotaConsumptionRequest request);
}
