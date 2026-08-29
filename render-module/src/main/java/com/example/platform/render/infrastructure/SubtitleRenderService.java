package com.example.platform.render.infrastructure;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SubtitleRenderService {
    private final SubtitleBurnInService burnInService;

    public SubtitleRenderService(SubtitleBurnInService burnInService) {
        this.burnInService = burnInService;
    }

    public List<String> checkSubtitleCompatibility(List<Map<String, Object>> subtitleTracks) {
        return burnInService.checkSubtitleCompatibility(subtitleTracks);
    }

    public String resolveFontFile(String fontFilePath) {
        return burnInService.resolveFontFile(fontFilePath);
    }
}
