package com.example.platform.render.infrastructure;

import java.util.Map;

public record EffectProviderMapping(
        String effectKey,
        String providerKey,
        String nativeName,
        Map<String, String> paramMapping
) {}
