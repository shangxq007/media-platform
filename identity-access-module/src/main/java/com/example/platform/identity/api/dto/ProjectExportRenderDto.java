package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ProjectExportRenderDto(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("renderPlan") Map<String, Object> renderPlan,
        @JsonProperty("spatialPlan") Map<String, Object> spatialPlan,
        @JsonProperty("effectTaxonomyVersion") String effectTaxonomyVersion
) {}
