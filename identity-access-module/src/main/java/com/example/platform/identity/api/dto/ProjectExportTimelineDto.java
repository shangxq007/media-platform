package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record ProjectExportTimelineDto(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("tracks") List<Map<String, Object>> tracks,
        @JsonProperty("totalDurationMs") double totalDurationMs
) {}
