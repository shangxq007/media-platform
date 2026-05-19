package com.example.platform.render.infrastructure;

import java.util.List;
import java.util.Map;

public record EffectDescriptor(
        String effectKey,
        String displayName,
        String category,
        String description,
        List<EffectParameterSchema> paramSchemas,
        List<String> providerKeys,
        Map<String, Object> defaultParams
) {}
