package com.example.platform.colorimage;

/**
 * ROADMAP_18 (CI22/CIC4): typed alpha interpretation. UNSPECIFIED means alpha
 * EXISTS but interpretation is unknown — never "presence unknown". Consistency
 * with RasterSampleDescription.alphaComponentPresent is enforced by
 * SourceVisualDescription construction.
 */
public enum AlphaDescription {
    NO_ALPHA, STRAIGHT, PREMULTIPLIED, UNSPECIFIED
}
