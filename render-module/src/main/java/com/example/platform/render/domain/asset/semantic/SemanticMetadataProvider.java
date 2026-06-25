package com.example.platform.render.domain.asset.semantic;

/**
 * SPI for semantic metadata providers (Probe, ASR, OCR, Vision, Embedding).
 *
 * <p>Each provider implements this interface to analyze media assets and
 * produce structured semantic metadata.</p>
 */
public interface SemanticMetadataProvider {

    /**
     * Whether this provider can analyze the given asset.
     */
    boolean supports(SemanticMetadataRequest request);

    /**
     * Analyze the asset and produce semantic metadata.
     */
    SemanticMetadataResult analyze(SemanticMetadataRequest request);

    /**
     * Human-readable provider name (e.g., "ffprobe", "WhisperProvider").
     */
    String providerName();

    /**
     * The capability category this provider belongs to.
     */
    SemanticCapability capability();
}
