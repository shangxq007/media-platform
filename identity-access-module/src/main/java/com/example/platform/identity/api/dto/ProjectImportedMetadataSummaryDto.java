package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary of imported metadata for a project.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportedMetadataSummaryDto(
        @JsonProperty("importId") String importId,
        @JsonProperty("sourceProjectId") String sourceProjectId,
        @JsonProperty("sourceExportId") String sourceExportId,
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("timelinePresent") boolean timelinePresent,
        @JsonProperty("timelineOtioPresent") boolean timelineOtioPresent,
        @JsonProperty("renderPlanPresent") boolean renderPlanPresent,
        @JsonProperty("spatialPlanPresent") boolean spatialPlanPresent,
        @JsonProperty("exportProfilesPresent") boolean exportProfilesPresent,
        @JsonProperty("effectTaxonomyPresent") boolean effectTaxonomyPresent,
        @JsonProperty("appliedEffectsPresent") boolean appliedEffectsPresent,
        @JsonProperty("assetMappingPresent") boolean assetMappingPresent,
        @JsonProperty("assetsNeedUpload") boolean assetsNeedUpload,
        @JsonProperty("createdAt") String createdAt
) {}
