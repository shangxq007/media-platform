package com.example.platform.billing.usage;

/** Explicit calculation category retained with every billable usage result. */
public enum MeteringTransformationKind {
    IDENTITY,
    SCALE,
    ROUND_UP_INCREMENT,
    EXCLUDE
}
