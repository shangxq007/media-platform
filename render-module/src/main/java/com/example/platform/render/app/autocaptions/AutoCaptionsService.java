package com.example.platform.render.app.autocaptions;

import com.example.platform.ai.api.video.SpeechToTextPort;
import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import com.example.platform.shared.Ids;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AutoCaptionsService {

    private static final Logger log = LoggerFactory.getLogger(AutoCaptionsService.class);

    private final SpeechToTextPort speechToText;

    public AutoCaptionsService(SpeechToTextPort speechToText) {
        this.speechToText = speechToText;
    }

    public AutoCaptionsResult generateCaptions(AutoCaptionsRequest request) {
        log.info("Auto Captions: generating for tenant={} project={} asset={}",
                request.tenantId(), request.projectId(), request.assetId());

        SpeechToTextPort.TranscribeRequest transcribeRequest = new SpeechToTextPort.TranscribeRequest(
                request.audioUri(),
                request.language() != null ? request.language() : "en",
                true,
                request.maxSegmentDurationMs() > 0 ? request.maxSegmentDurationMs() : 10000);

        SpeechToTextPort.SpeechToTextResult sttResult;
        try {
            sttResult = speechToText.transcribe(transcribeRequest);
        } catch (Exception e) {
            log.error("Auto Captions: transcription failed for project={}: {}",
                    request.projectId(), e.getMessage());
            return new AutoCaptionsResult(
                    request.projectId(), List.of(), 0, e.getMessage());
        }

        String fontFamily = request.fontFamily() != null ? request.fontFamily() : "Inter";
        int fontSize = request.fontSize() > 0 ? request.fontSize() : 24;
        String fontColor = request.fontColor() != null ? request.fontColor() : "#FFFFFF";
        String posX = request.positionX() > 0 ? String.valueOf(request.positionX()) : "center";
        String posY = request.positionY() > 0 ? String.valueOf(request.positionY()) : "bottom";

        List<TimelineTextOverlay> overlays = new ArrayList<>();
        for (SpeechToTextPort.SubtitleSegment seg : sttResult.segments()) {
            double startTimeSec = seg.startTimeMs() / 1000.0;
            double durationSec = (seg.endTimeMs() - seg.startTimeMs()) / 1000.0;

            TimelineTextOverlay overlay = new TimelineTextOverlay(
                    Ids.newId("sub"),
                    seg.text(),
                    fontFamily, fontSize, fontColor,
                    posX, posY,
                    startTimeSec, durationSec,
                    null);
            overlays.add(overlay);
        }

        log.info("Auto Captions: generated {} segments for project={}",
                overlays.size(), request.projectId());

        return new AutoCaptionsResult(
                request.projectId(), overlays, sttResult.segments().size(), null);
    }

    public record AutoCaptionsRequest(
            String tenantId,
            String projectId,
            String assetId,
            String audioUri,
            String language,
            int maxSegmentDurationMs,
            String fontFamily,
            int fontSize,
            String fontColor,
            double positionX,
            double positionY) {}

    public record AutoCaptionsResult(
            String projectId,
            List<TimelineTextOverlay> overlays,
            int segmentCount,
            String error) {

        public boolean success() {
            return error == null;
        }
    }
}
