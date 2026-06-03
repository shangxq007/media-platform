package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record ProjectExportOutputsDto(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("count") int count,
        @JsonProperty("outputs") List<Map<String, Object>> outputs
) {}
