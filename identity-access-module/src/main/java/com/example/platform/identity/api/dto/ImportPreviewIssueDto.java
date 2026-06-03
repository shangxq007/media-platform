package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportPreviewIssueDto(
        @JsonProperty("code") String code,
        @JsonProperty("severity") String severity,
        @JsonProperty("message") String message,
        @JsonProperty("detail") String detail
) {}
