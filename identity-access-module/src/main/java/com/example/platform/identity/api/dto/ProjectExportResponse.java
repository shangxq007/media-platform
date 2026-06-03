package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectExportResponse(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("exportId") String exportId,
        @JsonProperty("exportMode") String exportMode,
        @JsonProperty("exportedAt") Instant exportedAt,
        @JsonProperty("manifest") ProjectExportManifestDto manifest,
        @JsonProperty("project") ProjectExportProjectDto project,
        @JsonProperty("assets") ProjectExportAssetsDto assets,
        @JsonProperty("timeline") ProjectExportTimelineDto timeline,
        @JsonProperty("render") ProjectExportRenderDto render,
        @JsonProperty("effects") ProjectExportEffectsDto effects,
        @JsonProperty("outputs") ProjectExportOutputsDto outputs,
        @JsonProperty("audit") ProjectExportAuditDto audit
) {}
