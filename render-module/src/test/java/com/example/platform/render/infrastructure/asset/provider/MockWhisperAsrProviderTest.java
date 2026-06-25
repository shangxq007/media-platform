package com.example.platform.render.infrastructure.asset.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.asset.semantic.SemanticMetadataRequest;
import com.example.platform.render.domain.asset.semantic.SemanticCapability;
import org.junit.jupiter.api.Test;

class MockWhisperAsrProviderTest {

    private final MockWhisperAsrProvider provider = new MockWhisperAsrProvider();

    @Test
    void shouldSupportVideo() {
        assertTrue(provider.supports(SemanticMetadataRequest.of("a1", "v1", "VIDEO", "s3://v.mp4")));
    }

    @Test
    void shouldSupportAudio() {
        assertTrue(provider.supports(SemanticMetadataRequest.of("a1", "v1", "AUDIO", "s3://a.mp3")));
    }

    @Test
    void shouldNotSupportImage() {
        assertFalse(provider.supports(SemanticMetadataRequest.of("a1", "v1", "IMAGE", "s3://i.png")));
    }

    @Test
    void shouldProduceTranscriptWithSegments() {
        var result = provider.analyze(SemanticMetadataRequest.of("a1", "v1", "VIDEO", "s3://v.mp4"));

        assertTrue(result.success());
        assertNotNull(result.semanticMetadata());
        assertEquals(1, result.semanticMetadata().transcripts().size());

        var transcript = result.semanticMetadata().transcripts().get(0);
        assertEquals("en", transcript.language());
        assertEquals(3, transcript.segments().size());
        assertEquals("SPEAKER_1", transcript.segments().get(0).speaker());
        assertEquals("SPEAKER_2", transcript.segments().get(2).speaker());
        assertTrue(transcript.confidence() > 0.9);
    }

    @Test
    void shouldReportAsrCapability() {
        assertEquals(SemanticCapability.ASR, provider.capability());
    }
}
