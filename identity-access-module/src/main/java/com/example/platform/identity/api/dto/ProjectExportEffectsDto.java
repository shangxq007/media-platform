package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record ProjectExportEffectsDto(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("effectTaxonomyVersion") String effectTaxonomyVersion,
        @JsonProperty("appliedEffects") List<Map<String, String>> appliedEffects
) {}
