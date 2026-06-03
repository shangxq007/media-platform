package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectExportPackageDto(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("exportMode") String exportMode,
        @JsonProperty("manifest") ProjectExportManifestDto manifest,
        @JsonProperty("project") ProjectExportProjectDto project,
        @JsonProperty("assets") ProjectExportAssetsDto assets,
        @JsonProperty("timeline") ProjectExportTimelineDto timeline,
        @JsonProperty("render") ProjectExportRenderDto render
) {}
