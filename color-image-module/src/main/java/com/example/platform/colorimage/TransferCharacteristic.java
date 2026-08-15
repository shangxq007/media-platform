package com.example.platform.colorimage;

/**
 * ROADMAP_18 (CI11): typed transfer characteristic. Provider values normalize
 * to these; UNSPECIFIED/UNKNOWN/NOT_APPLICABLE are distinct, never null soup,
 * never a silent default to BT.709.
 */
public enum TransferCharacteristic {
    LINEAR, BT709, SRGB, PQ, HLG, BT1361, LOG, LOG_SQRT,
    UNSPECIFIED, UNKNOWN, NOT_APPLICABLE
}
