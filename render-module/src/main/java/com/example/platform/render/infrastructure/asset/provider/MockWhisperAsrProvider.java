package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.render.domain.asset.semantic.*;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mock ASR provider for verifying the enrichment pipeline without Whisper runtime.
 *
 * <p>Returns a fixed transcript with speaker segments. In production, this
 * would be replaced by a real Whisper or Deepgram integration.</p>
 */
@Component
public class MockWhisperAsrProvider implements SemanticMetadataProvider {

    @Override
    public boolean supports(SemanticMetadataRequest request) {
        return "VIDEO".equals(request.assetType()) || "AUDIO".equals(request.assetType());
    }

    @Override
    public SemanticMetadataResult analyze(SemanticMetadataRequest request) {
        TranscriptSegment seg1 = new TranscriptSegment(0, 5000, "SPEAKER_1",
                "Welcome to the quarterly review. We'll cover revenue growth and product updates.");
        TranscriptSegment seg2 = new TranscriptSegment(5000, 10000, "SPEAKER_1",
                "Q4 revenue reached $12.3 million, representing 15% year-over-year growth.");
        TranscriptSegment seg3 = new TranscriptSegment(10000, 15000, "SPEAKER_2",
                "Our AI-powered editing features are now in beta. Early feedback is positive.");

        Transcript transcript = Transcript.of(providerName(), "en",
                "Welcome to the quarterly review. We'll cover revenue growth and product updates. "
                        + "Q4 revenue reached $12.3 million, representing 15% year-over-year growth. "
                        + "Our AI-powered editing features are now in beta. Early feedback is positive.",
                0.95, List.of(seg1, seg2, seg3));

        AssetSemanticMetadata meta = new AssetSemanticMetadata(
                request.assetId(), request.assetVersion(),
                AssetSemanticMetadata.EnrichmentStatus.COMPLETE, "en",
                List.of(transcript), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                java.time.Instant.now(), java.time.Instant.now());

        return SemanticMetadataResult.success(meta, providerName());
    }

    @Override
    public String providerName() {
        return "mock-whisper-asr";
    }

    @Override
    public SemanticCapability capability() {
        return SemanticCapability.ASR;
    }
}
