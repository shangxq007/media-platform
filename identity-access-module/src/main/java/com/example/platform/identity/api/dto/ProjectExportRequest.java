package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Request to create a project export.
 *
 * @param mode export mode: metadata_only, linked_assets, bundled_assets, render_reproduction
 */
public record ProjectExportRequest(
        @JsonProperty("mode") String mode,
        @JsonProperty("signedUrlTtlSeconds") Integer signedUrlTtlSeconds
) {
    public static final String MODE_METADATA_ONLY = "metadata_only";
    public static final String MODE_LINKED_ASSETS = "linked_assets";
    public static final String MODE_BUNDLED_ASSETS = "bundled_assets";
    public static final String MODE_RENDER_REPRODUCTION = "render_reproduction";

    public boolean isMetadataOnly() {
        return MODE_METADATA_ONLY.equals(mode);
    }

    public boolean isLinkedAssets() {
        return MODE_LINKED_ASSETS.equals(mode);
    }

    public boolean isSupported() {
        return MODE_METADATA_ONLY.equals(mode) || MODE_LINKED_ASSETS.equals(mode);
    }
}
