package com.example.platform.colorimage;

/**
 * ROADMAP_18 (CI13): typed signal range. Never inferred from pixel format /
 * codec / family / bit depth. Missing metadata stays UNSPECIFIED.
 */
public enum SignalRange {
    FULL, LIMITED, UNSPECIFIED, UNKNOWN
}
