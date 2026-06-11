package com.example.platform.render.infrastructure.otio;

import java.util.Map;

public record OTIOEffectRef(
        String effectRefId,
        String effectId,
        String effectVersion,
        double startTime,
        double duration,
        Map<String, Object> params
) {}
