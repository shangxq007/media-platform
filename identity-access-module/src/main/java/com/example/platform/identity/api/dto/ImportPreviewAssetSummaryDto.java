package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportPreviewAssetSummaryDto(
        @JsonProperty("total") int total,
        @JsonProperty("available") int available,
        @JsonProperty("needsUpload") int needsUpload,
        @JsonProperty("missing") int missing
) {}
