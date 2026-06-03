package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectExportAssetDto(
        @JsonProperty("assetId") String assetId,
        @JsonProperty("filename") String filename,
        @JsonProperty("type") String type,
        @JsonProperty("mimeType") String mimeType,
        @JsonProperty("sizeBytes") long sizeBytes,
        @JsonProperty("sha256") String sha256,
        @JsonProperty("duration") Double duration,
        @JsonProperty("width") Integer width,
        @JsonProperty("height") Integer height,
        @JsonProperty("storageRef") String storageRef,
        @JsonProperty("downloadUrl") String downloadUrl
) {}
