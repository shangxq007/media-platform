package com.example.platform.render.domain.asset;

import java.util.List;
import java.util.Map;

/**
 * Minimal BMF graph definition — media pipeline description.
 * BMF operators are internal to BMF, NOT platform concepts.
 */
public record BmfGraphDefinition(
        String graphType,
        Map<String, Object> parameters,
        List<MediaInput> inputs,
        List<MediaOutput> outputs) {

    public record MediaInput(String storageUri, String mediaType, String checksum, long sizeBytes) {}
    public record MediaOutput(String label, String mediaType, String outputKey) {}

    public static BmfGraphDefinition transcode(String inputUri, String outputKey, String format) {
        return new BmfGraphDefinition("TRANSCODE",
                Map.of("format", format),
                List.of(new MediaInput(inputUri, "VIDEO", null, 0)),
                List.of(new MediaOutput("output", "VIDEO", outputKey)));
    }

    public static BmfGraphDefinition thumbnail(String inputUri, int width, int height) {
        return new BmfGraphDefinition("THUMBNAIL",
                Map.of("width", width, "height", height),
                List.of(new MediaInput(inputUri, "VIDEO", null, 0)),
                List.of(new MediaOutput("thumb", "IMAGE", "thumbnail")));
    }
}
