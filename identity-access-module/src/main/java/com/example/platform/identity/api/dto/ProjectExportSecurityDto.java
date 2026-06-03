package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectExportSecurityDto(
        @JsonProperty("containsSignedUrls") boolean containsSignedUrls,
        @JsonProperty("containsMedia") boolean containsMedia,
        @JsonProperty("containsSecrets") boolean containsSecrets,
        @JsonProperty("containsCredentials") boolean containsCredentials,
        @JsonProperty("promptRedacted") boolean promptRedacted,
        @JsonProperty("historyRedacted") boolean historyRedacted,
        @JsonProperty("storageRefsRedacted") boolean storageRefsRedacted
) {}
