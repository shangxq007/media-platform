package com.example.platform.ai.app.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.ai.api.video.HighlightDetectionPort;
import com.example.platform.ai.api.video.SilenceDetectionPort;
import com.example.platform.ai.api.video.SpeechToTextPort;
import com.example.platform.ai.api.video.SubtitleTranslationPort;
import com.example.platform.ai.api.video.VideoUnderstandingPort;
import com.example.platform.ai.infrastructure.video.NoopHighlightDetectionProvider;
import com.example.platform.ai.infrastructure.video.NoopSilenceDetectionProvider;
import com.example.platform.ai.infrastructure.video.NoopSpeechToTextProvider;
import com.example.platform.ai.infrastructure.video.NoopSubtitleTranslationProvider;
import com.example.platform.ai.infrastructure.video.NoopVideoUnderstandingProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiVideoCapabilitiesServiceTest {

    private AiVideoCapabilitiesService service;

    @BeforeEach
    void setUp() {
        service = new AiVideoCapabilitiesService(
                new NoopSpeechToTextProvider(),
                new NoopSubtitleTranslationProvider(),
                new NoopSilenceDetectionProvider(),
                new NoopHighlightDetectionProvider(),
                new NoopVideoUnderstandingProvider());
    }

    @Test
    void transcribeReturnsStubResult() {
        var request = new SpeechToTextPort.TranscribeRequest(
                "tenant/t1/project/p1/assets/a1/audio.wav", "en", true, 10000);
        var result = service.transcribe(request);

        assertNotNull(result);
        assertEquals("en", result.language());
        assertFalse(result.segments().isEmpty());
    }

    @Test
    void translateSubtitlesReturnsStubResult() {
        var segments = List.of(
                new SubtitleTranslationPort.SubtitleInput(0, 0, 5000, "Hello world"),
                new SubtitleTranslationPort.SubtitleInput(1, 5000, 10000, "How are you?"));
        var request = new SubtitleTranslationPort.TranslationRequest(segments, "en", "zh");
        var result = service.translateSubtitles(request);

        assertNotNull(result);
        assertEquals("zh", result.targetLanguage());
        assertEquals(2, result.segments().size());
    }

    @Test
    void detectSilenceReturnsEmptyStub() {
        var request = new SilenceDetectionPort.SilenceDetectionRequest(
                "tenant/t1/project/p1/assets/a1/audio.wav", -40.0, 500);
        var result = service.detectSilence(request);

        assertNotNull(result);
        assertTrue(result.regions().isEmpty());
    }

    @Test
    void detectHighlightsReturnsEmptyStub() {
        var request = new HighlightDetectionPort.HighlightDetectionRequest(
                "video-uri", "audio-uri", List.of(), 5, 3000, 30000);
        var result = service.detectHighlights(request);

        assertNotNull(result);
        assertTrue(result.highlights().isEmpty());
    }

    @Test
    void analyzeVideoReturnsStubResult() {
        var request = new VideoUnderstandingPort.VideoUnderstandingRequest(
                "video-uri", "audio-uri", List.of(), 10, "full");
        var result = service.analyzeVideo(request);

        assertNotNull(result);
        assertNotNull(result.summary());
    }

    private static void assertFalse(boolean condition) {
        assertTrue(!condition);
    }
}
