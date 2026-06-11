package com.example.platform.render.infrastructure.remotion;

import java.util.List;
import java.util.Map;

public record RemotionInputProps(
        int compositionWidth,
        int compositionHeight,
        int fps,
        int durationInFrames,
        List<RemotionCaption> captions,
        List<RemotionFontSpec> fontSpecs,
        RemotionTemplateSpec template,
        String outputFormat,
        Map<String, Object> extra
) {}
