package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProjectExportAssetsDto(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("exportMode") String exportMode,
        @JsonProperty("assets") List<ProjectExportAssetDto> assets,
        @JsonProperty("signedUrls") Object signedUrls
) {}
