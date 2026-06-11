package com.example.platform.render.infrastructure.api;

import java.util.List;

public record PublicCaption(
        String text,
        double startTime,
        double endTime,
        List<PublicCaptionWord> words
) {}
