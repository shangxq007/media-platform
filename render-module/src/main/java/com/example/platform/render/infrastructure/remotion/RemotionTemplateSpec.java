package com.example.platform.render.infrastructure.remotion;

import java.util.List;
import java.util.Map;

public record RemotionTemplateSpec(
        String templateId,
        String templateVersion,
        Map<String, Object> params,
        String compositionId
) {}
