package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.libass.LibassAssFileWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class SubtitleRenderService {
    private static final Logger log = LoggerFactory.getLogger(SubtitleRenderService.class);

    private final SubtitleBurnInService burnInService;
    private final LibassAssFileWriter libassAssFileWriter = new LibassAssFileWriter();

    @Value("${render.subtitle.libass.enabled:true}")
    private boolean libassEnabled;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public SubtitleRenderService(SubtitleBurnInService burnInService) {
        this.burnInService = burnInService;
    }

    public String buildSubtitleFilter(List<Map<String, Object>> subtitleTracks) {
        if (libassEnabled) {
            log.debug("SubtitleRenderService: libass enabled; drawtext fallback for legacy cue maps");
        }
        return burnInService.buildSubtitleFilter(subtitleTracks);
    }

    /** Writes ASS sidecar for L6 libass pipeline stage. */
    public Path prepareLibassStage(String jobId, TimelineSpec timeline) {
        try {
            int width = timeline.outputSpec() != null ? timeline.outputSpec().width() : 1920;
            int height = timeline.outputSpec() != null ? timeline.outputSpec().height() : 1080;
            Path assPath = Path.of(storageRoot, "artifacts", jobId, "pipeline-burn-in.ass");
            libassAssFileWriter.write(assPath, timeline.textOverlays(), width, height);
            log.info("Prepared libass ASS for job={} at {}", jobId, assPath);
            return assPath;
        } catch (Exception e) {
            log.warn("Failed to write libass ASS for job={}: {}", jobId, e.getMessage());
            return null;
        }
    }

    public List<String> checkSubtitleCompatibility(List<Map<String, Object>> subtitleTracks) {
        return burnInService.checkSubtitleCompatibility(subtitleTracks);
    }

    public String resolveFontFile(String fontFilePath) {
        return burnInService.resolveFontFile(fontFilePath);
    }
}
