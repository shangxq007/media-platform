package com.example.platform.ai.app.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.ai.api.video.AiVideoCapabilityUnavailableException;
import com.example.platform.ai.api.video.HighlightDetectionPort;
import com.example.platform.ai.api.video.SilenceDetectionPort;
import com.example.platform.ai.api.video.SpeechToTextPort;
import com.example.platform.ai.api.video.SpeechToTextUnavailableException;
import com.example.platform.ai.api.video.SubtitleTranslationPort;
import com.example.platform.ai.api.video.VideoUnderstandingPort;
import com.example.platform.ai.infrastructure.video.UnavailableHighlightDetectionProvider;
import com.example.platform.ai.infrastructure.video.UnavailableSilenceDetectionProvider;
import com.example.platform.ai.infrastructure.video.UnavailableSubtitleTranslationProvider;
import com.example.platform.ai.infrastructure.video.UnavailableVideoUnderstandingProvider;
import com.example.platform.ai.infrastructure.video.UnavailableSpeechToTextProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiVideoCapabilitiesServiceTest {

    private AiVideoCapabilitiesService service;

    @BeforeEach
    void setUp() {
        service = new AiVideoCapabilitiesService(
                new UnavailableSpeechToTextProvider(),
                new UnavailableSubtitleTranslationProvider(),
                new UnavailableSilenceDetectionProvider(),
                new UnavailableHighlightDetectionProvider(),
                new UnavailableVideoUnderstandingProvider());
    }

    @Test
    void transcribeFailsClosedWhenNoRealProviderExists() {
        var request = new SpeechToTextPort.TranscribeRequest(
                "tenant/t1/project/p1/assets/a1/audio.wav", "en", true, 10000);

        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                SpeechToTextUnavailableException.class,
                () -> service.transcribe(request));

        assertEquals("AI-503-001", failure.getErrorCode().code());
    }

    @Test
    void translateSubtitlesFailsClosed() {
        var segments = List.of(
                new SubtitleTranslationPort.SubtitleInput(0, 0, 5000, "Hello world"),
                new SubtitleTranslationPort.SubtitleInput(1, 5000, 10000, "How are you?"));
        var request = new SubtitleTranslationPort.TranslationRequest(segments, "en", "zh");
        assertThrows(AiVideoCapabilityUnavailableException.class,
                () -> service.translateSubtitles(request));
    }

    @Test
    void detectSilenceFailsClosed() {
        var request = new SilenceDetectionPort.SilenceDetectionRequest(
                "tenant/t1/project/p1/assets/a1/audio.wav", -40.0, 500);
        assertThrows(AiVideoCapabilityUnavailableException.class,
                () -> service.detectSilence(request));
    }

    @Test
    void detectHighlightsFailsClosed() {
        var request = new HighlightDetectionPort.HighlightDetectionRequest(
                "video-uri", "audio-uri", List.of(), 5, 3000, 30000);
        assertThrows(AiVideoCapabilityUnavailableException.class,
                () -> service.detectHighlights(request));
    }

    @Test
    void analyzeVideoFailsClosed() {
        var request = new VideoUnderstandingPort.VideoUnderstandingRequest(
                "video-uri", "audio-uri", List.of(), 10, "full");
        assertThrows(AiVideoCapabilityUnavailableException.class,
                () -> service.analyzeVideo(request));
    }
}
