package com.example.platform.media.domain.probe;

import java.io.Serializable;
import java.util.List;

/**
 * RAW_PROBE_RESULT_IS_NOT_CANONICAL_MEDIA_AUTHORITY_V1 — raw provider
 * observation record.
 *
 * <p>A {@link MediaProbeObservation} captures provider/ffprobe/native probe
 * output as an opaque observation: provider name, provider-specific raw
 * payload (JSON/string), observation flags, warnings, error. It is NEVER
 * canonical media authority. The canonical source structural model is
 * produced from observations by the normalization boundary.
 */
public record MediaProbeObservation(
        String provider,
        String rawPayload,
        boolean valid,
        boolean clientExportCompatible,
        boolean normalizeRequired,
        List<String> warnings,
        String error) implements Serializable {

    public static MediaProbeObservation failed(String provider, String error) {
        return new MediaProbeObservation(provider, "", false, false, true,
                List.of(), error);
    }
}
