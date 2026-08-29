package com.example.platform.entitlement.domain;

/** Fail-closed quota mutation rejection reasons. */
public enum QuotaUsageRejectionReason {
    NEGATIVE_RESULT,
    LIMIT_EXCEEDED
}
