package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ProjectExportAuditDto(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("exportEventId") String exportEventId,
        @JsonProperty("exportedAt") Instant exportedAt,
        @JsonProperty("exportedBy") String exportedBy,
        @JsonProperty("action") String action
) {}
