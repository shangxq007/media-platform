package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectExportManifestAssetsDto(
        @JsonProperty("mode") String mode,
        @JsonProperty("count") int count,
        @JsonProperty("totalSizeBytes") long totalSizeBytes
) {}
