package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportPreviewResponse(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("compatible") boolean compatible,
        @JsonProperty("project") ImportPreviewProjectDto project,
        @JsonProperty("assets") ImportPreviewAssetSummaryDto assets,
        @JsonProperty("effects") ImportPreviewEffectSummaryDto effects,
        @JsonProperty("warnings") List<ImportPreviewIssueDto> warnings,
        @JsonProperty("errors") List<ImportPreviewIssueDto> errors
) {}
