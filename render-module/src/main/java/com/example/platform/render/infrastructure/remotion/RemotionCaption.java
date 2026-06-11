package com.example.platform.render.infrastructure.remotion;

import java.util.List;

public record RemotionCaption(
        String id,
        String text,
        double startTime,
        double endTime,
        RemotionCaptionStyle style,
        List<RemotionCaptionWord> words
) {
    public boolean hasWordLevelTiming() {
        return words != null && !words.isEmpty();
    }
}
