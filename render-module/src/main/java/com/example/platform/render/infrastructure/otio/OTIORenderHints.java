package com.example.platform.render.infrastructure.otio;

import java.util.List;
import java.util.Map;

public record OTIORenderHints(
        String outputFormat,
        int outputWidth,
        int outputHeight,
        int outputFps,
        String preferredNormalizeProvider,
        List<String> requiredCapabilities,
        Map<String, Object> extra
) {}
