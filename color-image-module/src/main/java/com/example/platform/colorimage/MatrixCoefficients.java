package com.example.platform.colorimage;

/**
 * ROADMAP_18 (CI12): typed matrix coefficients. NOT_APPLICABLE for RGB-like
 * semantics where matrix conversion does not apply. Never null for both
 * missing/unknown/not-applicable.
 */
public enum MatrixCoefficients {
    BT601, BT709, BT2020_NCL, BT2020_CL, SMPTE240M, FCC, IDENTITY, RGB,
    UNSPECIFIED, UNKNOWN, NOT_APPLICABLE
}
