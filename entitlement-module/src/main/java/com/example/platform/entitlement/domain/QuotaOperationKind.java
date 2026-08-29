package com.example.platform.entitlement.domain;

/** Immutable quota ledger operation kinds. */
public enum QuotaOperationKind {
    CONSUMPTION,
    ADJUSTMENT,
    REVERSAL,
    RECONCILIATION
}
