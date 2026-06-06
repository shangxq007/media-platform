package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response for project import execute from zip archive.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportExecuteResponse(
        @JsonProperty("importId") String importId,
        @JsonProperty("status") String status,
        @JsonProperty("targetProjectId") String targetProjectId,
        @JsonProperty("mode") String mode,
        @JsonProperty("assets") ProjectImportAssetSummaryDto assets,
        @JsonProperty("assetMappings") List<ProjectImportAssetMappingDto> assetMappings,
        @JsonProperty("warnings") List<String> warnings,
        @JsonProperty("metadata") ImportMetadataSummaryDto metadata
) {}
