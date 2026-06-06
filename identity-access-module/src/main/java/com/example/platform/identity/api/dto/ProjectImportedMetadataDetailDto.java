package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Detail preview of imported metadata.
 *
 * <p>Contains scrubbed JSON structures for read-only preview.
 * All sensitive fields are removed before response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportedMetadataDetailDto(
        @JsonProperty("summary") ProjectImportedMetadataSummaryDto summary,
        @JsonProperty("timeline") JsonNode timeline,
        @JsonProperty("timelineOtio") JsonNode timelineOtio,
        @JsonProperty("renderPlan") JsonNode renderPlan,
        @JsonProperty("spatialPlan") JsonNode spatialPlan,
        @JsonProperty("exportProfiles") JsonNode exportProfiles,
        @JsonProperty("effectTaxonomy") JsonNode effectTaxonomy,
        @JsonProperty("appliedEffects") JsonNode appliedEffects,
        @JsonProperty("assetMapping") Map<String, AssetMappingEntry> assetMapping,
        @JsonProperty("warnings") List<String> warnings
) {
    /**
     * Asset mapping entry.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssetMappingEntry(
            @JsonProperty("targetAssetId") String targetAssetId,
            @JsonProperty("status") String status
    ) {}
}
