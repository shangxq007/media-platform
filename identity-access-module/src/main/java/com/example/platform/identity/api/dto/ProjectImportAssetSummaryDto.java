package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Asset summary for import response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportAssetSummaryDto(
        @JsonProperty("total") int total,
        @JsonProperty("imported") int imported,
        @JsonProperty("needsUpload") int needsUpload,
        @JsonProperty("rebound") int rebound,
        @JsonProperty("skipped") int skipped
) {}
