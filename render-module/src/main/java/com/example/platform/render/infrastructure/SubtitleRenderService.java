package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SubtitleRenderService {
    private static final Logger log = LoggerFactory.getLogger(SubtitleRenderService.class);

    private final SubtitleBurnInService burnInService;

    public SubtitleRenderService(SubtitleBurnInService burnInService) {
        this.burnInService = burnInService;
    }

    public String buildSubtitleFilter(List<Map<String, Object>> subtitleTracks) {
        return burnInService.buildSubtitleFilter(subtitleTracks);
    }

    public List<String> checkSubtitleCompatibility(List<Map<String, Object>> subtitleTracks) {
        return burnInService.checkSubtitleCompatibility(subtitleTracks);
    }

    public String resolveFontFile(String fontFilePath) {
        return burnInService.resolveFontFile(fontFilePath);
    }
}
