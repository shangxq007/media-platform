package com.example.platform.ai.app.video;

import com.example.platform.ai.api.video.HighlightDetectionPort;
import com.example.platform.ai.api.video.SilenceDetectionPort;
import com.example.platform.ai.api.video.SpeechToTextPort;
import com.example.platform.ai.api.video.SubtitleTranslationPort;
import com.example.platform.ai.api.video.VideoUnderstandingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiVideoCapabilitiesService {

    private static final Logger log = LoggerFactory.getLogger(AiVideoCapabilitiesService.class);

    private final SpeechToTextPort speechToText;
    private final SubtitleTranslationPort subtitleTranslation;
    private final SilenceDetectionPort silenceDetection;
    private final HighlightDetectionPort highlightDetection;
    private final VideoUnderstandingPort videoUnderstanding;

    public AiVideoCapabilitiesService(
            SpeechToTextPort speechToText,
            SubtitleTranslationPort subtitleTranslation,
            SilenceDetectionPort silenceDetection,
            HighlightDetectionPort highlightDetection,
            VideoUnderstandingPort videoUnderstanding) {
        this.speechToText = speechToText;
        this.subtitleTranslation = subtitleTranslation;
        this.silenceDetection = silenceDetection;
        this.highlightDetection = highlightDetection;
        this.videoUnderstanding = videoUnderstanding;
    }

    public SpeechToTextPort.SpeechToTextResult transcribe(SpeechToTextPort.TranscribeRequest request) {
        log.info("Auto Captions: transcribing audio={}", request.audioUri());
        return speechToText.transcribe(request);
    }

    public SubtitleTranslationPort.TranslationResult translateSubtitles(
            SubtitleTranslationPort.TranslationRequest request) {
        log.info("Subtitle Translation: {} → {}", request.sourceLanguage(), request.targetLanguage());
        return subtitleTranslation.translate(request);
    }

    public SilenceDetectionPort.SilenceDetectionResult detectSilence(
            SilenceDetectionPort.SilenceDetectionRequest request) {
        log.info("Silence Detection: audio={}", request.audioUri());
        return silenceDetection.detectSilence(request);
    }

    public HighlightDetectionPort.HighlightDetectionResult detectHighlights(
            HighlightDetectionPort.HighlightDetectionRequest request) {
        log.info("Highlight Detection: video={}", request.videoUri());
        return highlightDetection.detectHighlights(request);
    }

    public VideoUnderstandingPort.VideoUnderstandingResult analyzeVideo(
            VideoUnderstandingPort.VideoUnderstandingRequest request) {
        log.info("Video Understanding: video={} type={}", request.videoUri(), request.analysisType());
        return videoUnderstanding.analyze(request);
    }
}
