package com.example.platform.render.domain.timeline.compile.remotion;

/**
 * Remotion caption specification.
 * Internal only.
 */
public record RemotionCaptionSpec(
        String captionLayerId,
        String text,
        double startSeconds,
        double endSeconds,
        String fontFamily,
        int fontSize,
        String color,
        String positionX,
        String positionY,
        String backgroundColor) {}
