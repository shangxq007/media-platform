package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary of metadata persistence for import response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportMetadataSummaryDto(
        @JsonProperty("timelinePersisted") boolean timelinePersisted,
        @JsonProperty("renderPlanPersisted") boolean renderPlanPersisted,
        @JsonProperty("spatialPlanPersisted") boolean spatialPlanPersisted,
        @JsonProperty("effectMetadataPersisted") boolean effectMetadataPersisted
) {}
