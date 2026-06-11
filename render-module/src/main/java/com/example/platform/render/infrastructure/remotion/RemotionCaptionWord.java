package com.example.platform.render.infrastructure.remotion;

import java.util.List;

public record RemotionCaptionWord(
        String text,
        double startTime,
        double endTime,
        boolean highlighted
) {}
