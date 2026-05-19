package com.example.platform.render.domain;

import java.time.Instant;
import java.util.List;

public record SubtitleFont(String fontId, String family, String format,
                            String uploadedBy, Instant uploadedAt,
                            List<String> glyphCoverage, List<String> fallbackFontIds,
                            long fileSize, String checksum) {}
