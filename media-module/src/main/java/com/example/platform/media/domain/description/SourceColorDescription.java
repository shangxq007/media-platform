package com.example.platform.media.domain.description;

import java.io.Serializable;

/**
 * Source color metadata description (F3/#13-#18 boundary).
 *
 * <p>#13 owns SOURCE color description only (primaries / transfer / matrix /
 * range / HDR source descriptors as observed source metadata). Color
 * management policy and transforms belong to #18 Color/Image Foundation.
 * Unknown values are absent (null), never sentinel strings.
 */
public record SourceColorDescription(
        String primaries,
        String transfer,
        String matrix,
        String range,
        String hdrMasteringDisplayReference,
        String hdrContentLightReference) implements Serializable {
}
