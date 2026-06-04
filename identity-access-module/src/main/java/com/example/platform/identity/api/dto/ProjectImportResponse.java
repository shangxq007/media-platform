package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportResponse(
        @JsonProperty("importId") String importId,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("projectId") String projectId,
        @JsonProperty("mode") String mode,
        @JsonProperty("assetMappings") Map<String, String> assetMappings,
        @JsonProperty("assets") ProjectImportAssetResultDto assets,
        @JsonProperty("warnings") List<ImportPreviewIssueDto> warnings
) {}
