package com.example.platform.colorimage;

/**
 * ROADMAP_18 (CI23): typed normalized source orientation — source
 * interpretation, distinct from Timeline editorial rotation. No raw EXIF
 * integer/matrix values.
 */
public enum SourceOrientation {
    NORMAL, FLIP_HORIZONTAL, ROTATE_180, FLIP_VERTICAL,
    TRANSPOSE, ROTATE_90_CW, TRANSVERSE, ROTATE_90_CCW, UNSPECIFIED
}
