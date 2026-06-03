package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportPreviewProjectDto(
        @JsonProperty("sourceProjectId") String sourceProjectId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description
) {}
