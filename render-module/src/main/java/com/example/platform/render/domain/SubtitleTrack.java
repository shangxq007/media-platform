package com.example.platform.render.domain;

import java.util.List;

public record SubtitleTrack(String id, String language, List<SubtitleCue> cues,
                             String fontId, List<String> fallbackFontIds,
                             boolean burnIn, String externalFileUrl) {}
