package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportAssetResultDto(
        @JsonProperty("imported") int imported,
        @JsonProperty("rebound") int rebound,
        @JsonProperty("skipped") int skipped
) {}
