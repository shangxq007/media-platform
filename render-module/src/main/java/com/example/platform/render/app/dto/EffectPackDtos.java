package com.example.platform.render.app.dto;

import java.util.List;
import java.util.Map;

public final class EffectPackDtos {

    private EffectPackDtos() {}

    public record EffectPackEffectDto(
            String effectKey,
            String displayName,
            String category,
            String description,
            Map<String, Object> parameterSchema,
            Map<String, Object> defaultValues,
            List<String> providerMappings,
            List<String> allowedTiers,
            String taxonomyCategory,
            Boolean isEffect) {}

    public record EffectPackDto(
            String packId,
            String version,
            String name,
            String description,
            String author,
            String compatibility,
            List<String> allowedTiers,
            boolean builtin,
            String tenantId,
            List<EffectPackEffectDto> effects) {}

    public record CreateEffectPackRequest(
            String packId,
            String version,
            String name,
            String description,
            String author,
            String compatibility,
            List<String> allowedTiers,
            List<EffectPackEffectDto> effects) {}

    public record UpdateEffectPackRequest(
            String name,
            String description,
            String author,
            String compatibility,
            List<String> allowedTiers,
            List<EffectPackEffectDto> effects) {}
}
