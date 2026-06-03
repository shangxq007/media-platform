package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ProjectExportProjectDto(
        @JsonProperty("projectId") String projectId,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt,
        @JsonProperty("status") String status
) {}
