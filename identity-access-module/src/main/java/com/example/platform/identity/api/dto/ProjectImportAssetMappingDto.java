package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Asset mapping for import response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportAssetMappingDto(
        @JsonProperty("sourceAssetId") String sourceAssetId,
        @JsonProperty("targetAssetId") String targetAssetId,
        @JsonProperty("status") String status
) {}
