package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectExportManifestDto(
        @JsonProperty("$schema") String schema,
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("exportId") String exportId,
        @JsonProperty("exportMode") String exportMode,
        @JsonProperty("exportedAt") Instant exportedAt,
        @JsonProperty("exportedBy") String exportedBy,
        @JsonProperty("compatibility") Map<String, String> compatibility,
        @JsonProperty("security") ProjectExportSecurityDto security,
        @JsonProperty("assets") ProjectExportManifestAssetsDto assetsInfo,
        @JsonProperty("checksums") Map<String, String> checksums
) {}
