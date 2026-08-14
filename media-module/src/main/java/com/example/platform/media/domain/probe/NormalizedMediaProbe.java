package com.example.platform.media.domain.probe;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStream;
import com.example.platform.shared.time.MediaTime;
import java.io.Serializable;
import java.util.List;

/**
 * Normalized probe result — the canonical source structural model produced by
 * the ingest normalization boundary.
 *
 * <p>Exact duration/rate use the frozen exact primitives (MediaTime /
 * FrameRate / RationalTime). VFR is nominal rational rate + isVfr. Raw
 * provider values are NOT part of this record.
 */
public record NormalizedMediaProbe(
        MediaAssetId mediaAssetId,
        MediaTime duration,
        String container,
        boolean isVfr,
        boolean clientExportCompatible,
        boolean normalizeRequired,
        List<MediaStream> streams) implements Serializable {

    public NormalizedMediaProbe {
        if (mediaAssetId == null) {
            throw new IllegalArgumentException("mediaAssetId must not be null");
        }
        if (streams == null) {
            streams = List.of();
        }
    }
}
