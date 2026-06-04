package com.example.platform.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectImportRequest(
        @JsonProperty("payload") ProjectExportPackageDto payload,
        @JsonProperty("mode") String mode,
        @JsonProperty("targetProjectId") String targetProjectId,
        @JsonProperty("createNewProject") Boolean createNewProject,
        @JsonProperty("projectNameOverride") String projectNameOverride,
        @JsonProperty("assetImportPolicy") String assetImportPolicy,
        @JsonProperty("assetMappings") Map<String, String> assetMappings,
        @JsonProperty("requireChecksum") Boolean requireChecksum,
        @JsonProperty("allowPartial") Boolean allowPartial,
        @JsonProperty("conflictPolicy") String conflictPolicy
) {
    public static final String POLICY_METADATA_ONLY = "metadata_only";
    public static final String POLICY_DOWNLOAD_AND_REGISTER = "download_and_register";
    public static final String POLICY_REQUIRE_EXISTING_MAPPING = "require_existing_mapping";
    public static final String CONFLICT_FAIL_IF_EXISTS = "fail_if_exists";
    public static final String CONFLICT_OVERWRITE = "overwrite_metadata";
}
