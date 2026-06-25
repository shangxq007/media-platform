package com.example.platform.render.domain.xmp;

/**
 * XMP {@code ai:*} namespace metadata.
 */
public record XmpAiMetadata(
        String model,
        String modelVersion,
        String prompt,
        String negativePrompt,
        String seed,
        String sampler,
        Double guidanceScale,
        String taskType,
        Double confidence,
        String reviewStatus) {
}
