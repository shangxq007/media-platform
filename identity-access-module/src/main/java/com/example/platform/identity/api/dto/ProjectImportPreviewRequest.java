package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportPreviewRequest(
        @JsonProperty("exportPackage") ProjectExportPackageDto exportPackage
) {}
