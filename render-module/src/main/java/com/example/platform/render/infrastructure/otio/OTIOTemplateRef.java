package com.example.platform.render.infrastructure.otio;

import java.util.Map;

public record OTIOTemplateRef(
        String templateRefId,
        String templateId,
        String templateVersion,
        Map<String, Object> params
) {}
